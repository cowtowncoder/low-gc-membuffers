# Overview

This project aims at creating a simple efficient building block for "Big Data" libraries, applications and frameworks; thing that can be used as an in-memory, bounded queue with opaque values (byte sequences): insertions at tail, removal from head, no lookups), and that has minimal garbage collection overhead.

GC overhead minimization is achieved by use of direct ByteBuffers (memory allocated outside of GC-prone heap); and bounded nature by only supporting storage of simple byte sequences where size is explicitly known.

Conceptually memory buffers are just simple circular buffers (ring buffers); library supports efficient reusing and sharing of underlying segments for sets of buffers, although for many use cases single buffer suffices.

# Fancier stuff: multiple buffers

Although having individual buffers is useful as is, this library does bit better: it actually defines "buffer groups" (com.fasterxml.membuf.MemBuffers) that consist of zero or more actual buffers (com.fasterxml.membuf.MemBuffer). All buffers of a group share the same segment allocator (com.fasterxml.membuf.SegmentAllocator); which makes it possible to share set of reusable underlying ByteBuffers.

This ability to share underlying segments between buffers, with strict memory bounds makes it possible to use library as basic buffer manager; for example to buffer input and/or output of a web server.

# Status

Small set of unit tests exist, and code appears solid enough to start building test systems -- but do not yet do prod systems with it. :)

# Usage

It all starts with `MemBuffers`, which can be viewed as container and factory of actual buffers (`MemBuffer`). To construct one, you need to specify amount of memory to use, as well as how memory should be sliced: so, for example:

    MemBuffers bufs = new MemBuffers(30 * 1024, 2, 11);

would create instance that allocates at least 2 (and at most 11) segments (which wrap direct `ByteBuffer` instances) with size of 30 kB: that is, has memory usage between 60 and 330 kilobytes.
The segments are then used by actual buffer instances (more on this in a bit)

So how do you choose parameters? Smaller the segments, more granular is memory allocation, which can mean more efficient memory use (since overhead is bounded to at most 1 segment-full per active buffer). But it also increases number of segment instances, possibly increasing fragmentation and adding overhead.

Actual buffers are then allocated using

    MemBuffer items = bufs.createBuffer(2, 5);

which would indicate that this buffer will hold on to at least 2 segments (i.e. about 60kB raw storage) and use at most 5 (so max usage of 150kB). Due to circular buffer style of allocation, at least 'segments - 1' amount of memory will be available for actual queue; that is, up to one segment may be temporarily unavailable depending on pattern of append/remove operations.

To append entries, you use:

    byte[] dataEntry = ...; // serialize from, say, JSON
    items.appendEntry(dataEntry);

or, if you don't want an exception if there is no more room:

    if (!items.tryAppendEntry(dataEntry)) {
       // recover? Drop entry? Log?
    }

and to pop entries:

    byte[] next = items.getNextEntry(); // blocks if nothing available
    // or:
    next = items.getNextEntryIfAvailable();
    if (next == null) { // nothing yet available
        //...
    }
    // or:
    next = items.getNextEntry(1000L); // block for at most 1 second before giving up

