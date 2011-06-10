# Overview

This project aims at creating a simple efficient building block for "Big Data" libraries, applications and frameworks; thing that can be used as an in-memory, bounded queue with opaque values (byte sequences): insertions at tail, removal from head, no lookups), and that has minimal garbage collection overhead.

GC overhead minimization is achieved by use of direct ByteBuffers; and bounded nature by only supporting storage of simple byte sequences where size is explicitly known.

Conceptually memory buffers are just simple circular buffers (ring buffers); library supports efficient reusing and sharing of underlying segments for sets of buffers, although for many use cases single buffer suffices.

# Fancier stuff: multiple buffers

Although having individual buffers is useful as is, this library does bit better: it actually defines "buffer groups" (com.fasterxml.membuf.MemBuffers) that consist of zero or more actual buffers (com.fasterxml.membuf.MemBuffer). All buffers of a group share the same segment allocator (com.fasterxml.membuf.SegmentAllocator); which makes it possible to share set of reusable underlying ByteBuffers.

This ability to share underlying segments between buffers, with strict memory bounds makes it possible to use library as basic buffer manager; for example to buffer input and/or output of a web server.

# Usage

[TO BE WRITTEN!] -- till then, check out unit tests.

# Status

Just wrote first unit tests and managed to get them to pass -- so very close to being usable.
