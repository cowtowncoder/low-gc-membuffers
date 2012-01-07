/**
Package that contains public API of the the library.
<p>
For full usage, check out
<a href="https://github.com/cowtowncoder/low-gc-membuffers">project home</a>,
but here is an example of simple usage:
<p>

First thing you need is a factory to create {@link MemBuffer} instance you want.
Buffers come in multiple flavors, divided by two main criteria:

<ul>
  <li>Type of values buffered: one of primitive types like "byte" or "long"
   </li>
  <li>Bundling of values: either "chunky" (value sequence boundaries are preserved
so that each read matches append that added value sequence)
  and "streamy" (boundaries NOT preserved: stream-style)
   </li>
 </ul>

There is one factory for each value type, so we have:

<ul>
  <li>{@link MemBuffersForBytes} for <code>byte</code> sequence buffering
   </li>
  <li>{@link MemBuffersForLong} for <code>long</code> sequence buffering
   </li>
 </ul>

To create byte-based buffer, you will thus do:

<pre>
  // use segments of 64kB; allocate 2 first, allocate at most 16
  // (i.e. max memory usage ~1MB)
  MemBuffersForBytes bufs = new MemBuffersForBytes(64 * 1024, 2, 16);
</pre>

this factory object can create both "chunky" and "streamy" buffers: assuming you
want to preserve byte boundaries you would use:

<pre>
  // create buffer that starts with a single 64kB segment;
  // and can expand to up to 192kB
  ChunkyBytesMemBuffer items = bufs.createChunkyBuffer(1, 4);
</pre>

Buffer instances are then used to append entries, and read them in FIFO order:

<pre>
byte[] dataEntry = ...; // serialize from, say, JSON
items.appendEntry(dataEntry);
</pre>

Or if you don't want an exception if there is no more room:

<pre>
if (!items.tryAppendEntry(dataEntry)) {
   // recover? Drop entry? Log?
}
</pre>

and you can read appended entries in FIFO order:

<pre>
byte[] next = items.getNextEntry(); // blocks if nothing available
// or:
next = items.getNextEntryIfAvailable();
if (next == null) { // nothing yet available
    //...
}
// or:
next = items.getNextEntry(1000L); // block for at most 1 second before giving up
</pre>

And that's it for basics -- head to the
<a href="https://github.com/cowtowncoder/low-gc-membuffers">project home</a>,
for more info!
*/

package com.fasterxml.util.membuf;
