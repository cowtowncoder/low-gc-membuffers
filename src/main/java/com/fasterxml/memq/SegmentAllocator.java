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
    
    /**
     * And here we hold on to segments that have been returned by buffers
     * after use.
     */
    protected Segment _firstReusableSegment;
    
    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    public SegmentAllocator(int segmentSize, int minSegments, int maxSegments)
    {
        _segmentSize = segmentSize;
        // should we pre-allocated segments?
        _maxReusableSegments = minSegments;
        _maxSegmentsToAllocate = maxSegments;

        _bufferOwnedSegmentCount = 0;
        _reusableSegmentCount = 0;
        _firstReusableSegment = null;
    }

    /*
    /**********************************************************************
    /* API
    /**********************************************************************
     */

    public int getSegmentSize() { return _segmentSize; }
    
    /**
     * Method that will try to allocate a single segment and return it;
     * or if no allocation can be done (due to limits) return null.
     */
    public synchronized Segment allocateSegment() {
        return _canAllocate(1) ? _allocateSegment() : null;
    }
    
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
    public synchronized Segment allocateSegments(int count, Segment segmentList)
    {
        if (!_canAllocate(count)) {
            return null;
        }
        for (int i = 0; i < count; ++i) {
            segmentList = _allocateSegment().relink(segmentList);
        }
        return segmentList;
    }
    
    /**
     * Method called by {@link MemBuffer} instances when they have consumed
     * contents of a segment and do not want to hold on to it for local
     * reuse.
     */
    public synchronized void releaseSegment(Segment segToRelease)
    {
        if (--_bufferOwnedSegmentCount < 0) { // sanity check; not needed in perfect world
            int count = _bufferOwnedSegmentCount;
            _bufferOwnedSegmentCount = 0; // "fix"
            throw new IllegalStateException("Bugger! Corruption Maximus: _bufferOwnedSegmentCount went below 0 ("
                    +count+")");
        }
        // Can we reuse it?
        if (_reusableSegmentCount < _maxReusableSegments) {
            _firstReusableSegment = segToRelease.relink(_firstReusableSegment);
        }
    }
    
    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */

    protected Segment _allocateSegment()
    {
        // can reuse a segment returned earlier?
        if (_reusableSegmentCount > 0) {
            Segment segment = _firstReusableSegment;
            _firstReusableSegment = segment.getNext();
            --_reusableSegmentCount;
            segment.resetForReuse(null);
            return segment;
        }
        Segment segment = new Segment(_segmentSize);
        ++_bufferOwnedSegmentCount; 
        return segment;
    }
    
    protected boolean _canAllocate(int count)
    {
        int available = _reusableSegmentCount + (_maxSegmentsToAllocate - _bufferOwnedSegmentCount);
        return (available >= count);
    }
}
