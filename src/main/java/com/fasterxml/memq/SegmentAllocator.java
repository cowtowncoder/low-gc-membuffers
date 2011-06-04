package com.fasterxml.memq;

/*
 * Copyright Tatu Saloranta, 2011-
 */

/**
 * Shared allocator object, used by all {@link MemBuffer} instances
 * that are part of a {@link MemBuffers}. It handles allocation
 * of new {@link Segment} instances, as well as sharing of
 * shared segments (above and beyond simple reuse that individual
 * queues can do).
 */
public class SegmentAllocator
{
    protected final int _segmentSize;

    /**
     * Maximum number of segments we will retain so that they may
     * be reused by buffer. Anything release beyond this number
     * will be freed.
     */
    protected final int _maxReusableSegments;

    /**
     * Maximum number of segments that this allocator will allocate.
     */
    protected final int _maxSegmentsToAllocate;

    public SegmentAllocator(int segmentSize, int minSegments, int maxSegments)
    {
        _segmentSize = segmentSize;
        // should we pre-allocated segments?
        _maxReusableSegments = minSegments;
        _maxSegmentsToAllocate = maxSegments;
    }
}
