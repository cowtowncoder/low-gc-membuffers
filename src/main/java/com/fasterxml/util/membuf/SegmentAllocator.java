package com.fasterxml.util.membuf;

/*
 * Copyright Tatu Saloranta, 2011-
 */

/**
 * Shared allocator object, used by standardl {@link MemBuffer} implementations.
 * Handles allocation of new {@link Segment} instances, as well as sharing of
 * reusable segments (above and beyond simple reuse that individual
 * buffers can do).
 */
public abstract class SegmentAllocator<T extends Segment<T>>
{
    /*
    /**********************************************************************
    /* Configuration
    /**********************************************************************
     */
    
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

    /*
    /**********************************************************************
    /* State
    /**********************************************************************
     */

    /**
     * Number of segments we have allocated for buffers and that they
     * have not yet released.
     */
    protected int _bufferOwnedSegmentCount;

    /**
     * Number of segments we are holding for reuse.
     */
    protected int _reusableSegmentCount;
    
    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    /**
     * Constructor for creating simple allocator instances.
     * Basic configuration is used to limit amount of memory that will
     * be used (by {@link maxSegments}), as well as degree to which
     * allocator can reuse released segments (to reduce churn by freeing
     * and reallocating segments, as segments typically use native
     * {@link java.nio.ByteBuffer}s that may be costly to allocate.
     * 
     * @param segmentSize Length (in bytes) of segments to allocate
     * @param minSegmentsToRetain Number of segments that we will retain after
     *   being released, for reuse; if 0, will not reuse any segments (although
     *   individual buffers may still do local reuse)
     * @param maxSegments Maximum number of allocated (and not released) segments
     *   allowed at any given point;
     *   strictly limits maximum memory usage by all {@link MemBuffer}s that
     *   use this allocator.
     */
    public SegmentAllocator(int segmentSize, int minSegmentsToRetain, int maxSegments)
    {
        if (minSegmentsToRetain < 0 || minSegmentsToRetain > maxSegments) {
            throw new IllegalArgumentException("minSegmentsToRetain ("+minSegmentsToRetain
                    +") must be at least 0; can not exceed maxSegments ("+maxSegments+")");
        }
        
        _segmentSize = segmentSize;
        // should we pre-allocated segments?
        _maxReusableSegments = minSegmentsToRetain;
        _maxSegmentsToAllocate = maxSegments;

        _bufferOwnedSegmentCount = 0;
        _reusableSegmentCount = 0;
    }

    /*
    /**********************************************************************
    /* API
    /**********************************************************************
     */

    public final int getSegmentSize() { return _segmentSize; }

    // Used by unit tests
    public final int getReusableSegmentCount() { return _reusableSegmentCount; }
    public final int getBufferOwnedSegmentCount() { return _bufferOwnedSegmentCount; }
    
    /**
     * Method that will try to allocate specified number of segments
     * (and exactly that number; no less).
     * If this can be done, segments are allocated and prepended into
     * given segment list; otherwise nothing is allocated and
     * null is returned.
     * 
     * @param count Number of segments to allocate
     *   
     * @return Head of segment list (with newly allocated entries as first
     *    entries) if allocation succeeded; null if not
     */
    public abstract T allocateSegments(int count, T segmentList);
    
    /**
     * Method called by {@link MemBuffer} instances when they have consumed
     * contents of a segment and do not want to hold on to it for local
     * reuse.
     */
    public abstract void releaseSegment(T segToRelease);
}
