/**
Package that contains public API of the the library.
<p>
For full usage, check out
<a href="https://github.com/cowtowncoder/low-gc-membuffers">project home</a>,
but here is an example of simple usage:
<p>
First thing needed is creation of {@link com.fasterxml.util.membuf.MemBuffers} instance:

<pre>
  // use segments of 64kB; allocate 2 first, allocate at most 16
  // (i.e. max memory usage ~1MB)
  MemBuffers bufs = new MemBuffers(64 * 1024, 2, 16);
</pre>

this instance can be used for constructing {@link com.fasterxml.util.membuf.MemBuffer} instances:
<pre>
  // create buffer that starts with a single 64kB segment;
  // and can expand to up to 192kB
  MemBuffer items = bufs.createBuffer(1, 4);
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
