# Overview

This project aims at creating a simple efficient building block for "Big Data" libraries, applications and frameworks; thing that can be used as an in-memory, bounded queue with opaque queue (values as byte sequences, insertions at tail, removal from head, no lookups), and that has minimal garbage collection overhead.
Gc overhead minimization is achieved by use of direct ByteBuffers; and bounded nature by only supporting storage of simple byte sequences where size is explicitly known.

# Status

Just getting started -- idea is simple, will take a week or two to implement correctly.

# Other

for more details check out Wiki page at [https://github.com/ning/jvm-compressor-benchmark/wiki](Wiki))
