# Overview

This project aims at creating a simple efficient building block for "Big Data" libraries, applications and frameworks; thing that can be used as an in-memory, bounded queue with opaque values (sequence of JDK primitive values): insertions at tail, removal from head, single entry peeks), and that has minimal garbage collection overhead. Insertions and removals are as individual entries, which are sub-sequences of the full buffer.

GC overhead minimization is achieved by use of direct `ByteBuffer`s (memory allocated outside of GC-prone heap); and bounded nature by only supporting storage of simple primitive value (`byte`, `long') sequences where size is explicitly known.

Conceptually memory buffers are just simple circular buffers (ring buffers) that hold a sequence of primitive values, bit like arrays, but in a way that allows dynamic automatic resizings of the underlying storage.
Library supports efficient reusing and sharing of underlying segments for sets of buffers, although for many use cases a single buffer suffices.

Buffers vary in two dimensions:

1. Type of primitive value contained: currently `byte` and `long` variants are implemente, but others (like `int` or `char`) will be easy to add as needed
2. Whether sequences are "chunky" -- sequences consists of 'chunks' created by distinct `appendEntry()` calls (and retrieved in exactly same sized chunks with `getNextEntry()`) -- or "streamy", meaning that values are coalesced and form a logical stream (so multiple `appendEntry()` calls may be coalesced into just one entry returned by `getNextEntry()`).

Since Java has no support for "generic primitives", there are separate classes for all combinations.
This means that there are currently 4 flavors of buffers:

* for `byte` (using `MemBuffersForBytes`)
 * `ChunkyBytesMemBuffer`
 * `StreamyBytesMemBuffer`
* for `long` (using `MemBuffersForLongs`)
 * `ChunkyLongsMemBuffer`
 * `StreamyLongsMemBuffer`

Another thing that can vary is the way underlying segments are allocated; default is to use native ("direct") `ByteBuffer`s. But more on this later on.

## Licensing

Standard [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0.html) license.

## Fancier stuff: multiple buffers

Although having individual buffers is useful as is, this is just the beginning.
Conceptually library supports "buffer groups", sets of similary-valued buffer instances owned by a single factory (like `MemBuffersForBytes`) that share same segment allocator (`com.fasterxml.util.membuf.SegmentAllocator`).
This makes it possible to share set of reusable underlying `ByteBuffer` instances for buffers in the same group.

This ability to share underlying segments between buffers, with strict memory bounds makes it possible to use library as basic buffer manager; for example to buffer input and/or output of a web server (byte-based "streamy" buffers), or as simplistic event queues (usually using "chunky" buffers).

To have multiple buffer groups simply construct multiple factory instances.

## Thread-safety

All pieces are designed to be used by multiple threads (often just 2, producer/consumer), so all access is properly synchronized.

In addition, locking is done using buffer instances, so it may occasionally make sense to synchronize on buffer instance since this allows you to create atomic sequences of operations, like so:

    MemBuffersForBytes factory = new MemBuffersForBytes(...);
    ChunkyBytesMemBuffer buffer = factory.createChunkyBuffer(...);
    synchronized (buffer) {
      // read latest, add right back:
      byte[] msg = buffer.getNextEntry();
      buffer.appendEntry(msg);
    }

or similarly if you need to read a sequence of entries as atomic unit.

# Status

Project has been used by multiple production systems (by multiple companies) since 2012,
and by now has proven stable and performant for expected use cases.
As such it is considered production ready: the official 1.0 version was released in October 2013.

The first accessible project that uses it is [Arecibo](https://github.com/ning/Arecibo),
a metrics collection, aggregation and visualization.

Companies that use this library for production systems include:

* [Mode Media](http://www.modemediacorp.com/) (nee Glam Media)
* [Salesforce](http://www.salesforce.com/)

# Usage

## Getting it

To use with Maven, add:

```xml
<dependency>
  <groupId>com.fasterxml.util</groupId>
  <artifactId>low-gc-membuffers</artifactId>
  <version>1.1.1</version>
</dependency>
```

For downloadables, javadocs check out [Wiki](https://github.com/cowtowncoder/low-gc-membuffers/wiki).

## Start with a factory

Exact factory to use depends on value type: here we assume you are looking
for byte-based buffers. If so, you will use `MemBuffersForBytes`.
This object can be viewed as container and factory of actual buffers
(`ChunkyBytesMemBuffer` or `StreamyBytesMemBuffer`).
To construct one, you need to specify amount of memory to use, as well as how memory should be sliced: so, for example:

```java
MemBuffersForBytes factory = new MemBuffersForBytes(30 * 1024, 2, 11);
```

would create instance that allocates at least 2 (and at most 11) segments (which wrap direct `ByteBuffer` instances) with size of 30 kB: that is, has memory usage between 60 and 330 kilobytes.
The segments are then used by actual buffer instances (more on this in a bit)

So how do you choose parameters? Smaller the segments, more granular is memory allocation, which can mean more efficient memory use (since overhead is bounded to at most 1 segment-full per active buffer). But it also increases number of segment instances, possibly increasing fragmentation and adding overhead.

Note that you can create multiple instances of `MemBuffers[Type]` factories, if you want to have more control over how pool of segments is allocated amongst individual buffers.

### Detour: allocating underlying storage segments

By default segments are allocated as `ByteBuffer`s (or typed sub-types for `long`s and so on). But this behavior can be changed by passing alternate
`SegmentAllocator` instances.

For example, if you instead wanted to use in-heap segments stored as basic
byte arrays (`byte[]`), you could do this by:

```java
MemBuffersForBytes factory = new MemBuffersForBytes(
  ArrayBytesSegment.allocator(30 * 1024, 2, 11));
```

or to use non-direct `ByteBuffer`s:

```java
MemBuffersForBytes factory = new MemBuffersForBytes(
  ByteBufferBytesSegment.allocator(30 * 1024, 2, 11, false));
```

Note that `SegmentAllocator` instances are implemented as inner classes of
matching segment type, that is as `ArrayBytesSegment.Allocator` and
`ByteBufferBytesSegment.Allocator`.

Also note that neither `Allocator`s nor `MemBuffers` keep track of underlying
segments. What this means it that buffers MUST be closed (explicitly, or indirectly by using wrappers) to make sure segments are released for reuse.

## Create individual buffers, `MemBuffer`

Actual buffers are then allocated using

```java
ChunkyBytesMemBuffer items = bufs.createChunkyBuffer(2, 5);
```

which would indicate that this buffer will hold on to at least 2 segments (i.e. about 60kB raw storage) and use at most 5 (so max usage of 150kB).
Due to circular buffer style of allocation, at least 'segments - 1' amount of memory will be available for actual queue (i.e. guaranteed space of 120kB; that is, up to one segment may be temporarily unavailable depending on pattern of append/remove operations.

## And start buffering/unbuffering

To append entries, you use:

```java
byte[] dataEntry = ...; // serialize from, say, JSON
items.appendEntry(dataEntry);
```

or, if you don't want an exception if there is no more room:

```java
if (!items.tryAppendEntry(dataEntry)) {
   // recover? Drop entry? Log?
}
```

and to pop entries:

```java
byte[] next = items.getNextEntry(); // blocks if nothing available
// or:
next = items.getNextEntryIfAvailable();
if (next == null) { // nothing yet available
    //...
}
// or:
next = items.getNextEntry(1000L); // block for at most 1 second before giving up
```

## And make sure that...

You '''always close buffers''' when you are done with them -- otherwise underlying segments may be leaked. This because buffers are only objects that keep track of segments; and nothing keeps track of `MemBuffer` instances created -- this is intentional, as synchronization otherwise needed is very expensive from concurrency perspective.

Note that version 0.9.1 allows use of `MemBufferDecorator` instances, which makes it possible to build wrappers that can implement simple auto-closing of buffers.


## Statistics, anyone?

Finally, you can also obtain various statistics of buffer instances:

```java
int entries = items.getEntryCount(); // how many available for getting?
int segmentsInUse = items.getSegmentCount(); // nr of internal segments
long maxFree = items.getMaximumAvailableSpace(); // approximate free space
long payload = items.getTotalPayloadLength(); // how much used by data?
```

# Download

Check out [Wiki](https://github.com/cowtowncoder/low-gc-membuffers/wiki) for downloads, Javadocs etc.

# Known/potential problems

Default (and currently only) buffer implementation uses direct `ByteBuffer`s, and amount of memory that can be allocated is limited by JVM option `-XX:MaxDirectMemorySize`, which by default has relatively low size of 64megs.
To increase this setting, add setting like:

    -XX:MaxDirectMemorySize=512m

otherwise you are likely to hit an OutOfMemoryError when using larger buffers.

# Future ideas

Here are some improvement ideas:

* "Slab" allocation (issue #14): allow initial allocation of a longer off-heap memory segment, with size of `N` segments: this "slab" will be fixed and NOT dynamically allocated or freed; segments will be sub-allocated as needed.
    * Main benefit is reduced need for actually memory management (no per-operation `malloc` or `free`)
    * Adds fixed overhead: slab size needs to be balanced with costs
    * Segments from slabs allocated before dynamic segments, as they do not incur additional allocation or memory usage cost (due to fixed default overhead)
* Expose streamy byte buffers as `InputStream` (issue #19).
    * Would need to choose what happens with end-of-input: snapshot (expose current and as EOF) vs blocking (works like pipe)
