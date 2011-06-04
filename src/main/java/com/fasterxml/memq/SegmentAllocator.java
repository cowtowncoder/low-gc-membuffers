package com.fasterxml.memq;

import java.util.*;

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

    public synchronized Segment allocateSegment() {
        return null;
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

    public synchronized Segment allocateSegments(boolean errorOnFail)
    {
        if (!_canAllocate(1, errorOnFail)) {
            return null;
        }
        Segment segment = _allocateSegment();
        ++_bufferOwnedSegmentCount; 
        return segment;
    }
    
    /**
     * @param count Number of segments to allocate
     * @param errorOnFail Whether to throw {@link IllegalStateException} if allocation
     *   can be done (true); or just return null (false)
     *   
     * @return Either List containing specified number of Segments; or null if allocation
     *   can not be done.
     */
    public synchronized List<Segment> allocateSegments(int count, boolean errorOnFail)
    {
        if (!_canAllocate(count, errorOnFail)) {
            return null;
        }
        ArrayList<Segment> result = new ArrayList<Segment>(count);
        for (int i = 0; i < count; ++i) {
            result.add(_allocateSegment());
        }
        _bufferOwnedSegmentCount += count;        
        return result;
    }

    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */

    protected Segment _allocateSegment() {
        return new Segment(_segmentSize);
    }
    
    protected boolean _canAllocate(int count, boolean errorOnFail)
    {
        int available = _reusableSegmentCount + (_maxSegmentsToAllocate - _bufferOwnedSegmentCount);
        if (available < count) {
            if (errorOnFail) {
                throw new IllegalStateException("Request to allocate "+count+" segments; can allocate at most "+available);
            }
            return false;
        }
        return true;
    }
}
