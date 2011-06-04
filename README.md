# Overview

This project aims at creating a simple efficient building block for "Big Data" libraries, applications and frameworks; thing that can be used as an in-memory, bounded queue with opaque values (byte sequences): insertions at tail, removal from head, no lookups), and that has minimal garbage collection overhead.

GC overhead minimization is achieved by use of direct ByteBuffers; and bounded nature by only supporting storage of simple byte sequences where size is explicitly known.

Conceptually memory buffers are just simple circular buffers (ring buffers); library supports efficient reusing and sharing of underlying segments for sets of buffers, although for many use cases single buffer suffices.

# Status

Just getting started -- idea is simple, will take a week or two to implement correctly.
