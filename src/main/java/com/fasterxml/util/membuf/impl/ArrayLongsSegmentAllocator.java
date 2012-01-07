package com.fasterxml.util.membuf.impl;

import com.fasterxml.util.membuf.SegmentAllocator;
import com.fasterxml.util.membuf.base.LongsSegment;
import com.fasterxml.util.membuf.base.LongsSegmentAllocator;

/**
 * {@link SegmentAllocator} implementation that allocates
 * {@link ArrayBytesSegment}s, which are simple byte array backed segments.
 */
public class ArrayLongsSegmentAllocator extends LongsSegmentAllocator
{
    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */
    
    public ArrayLongsSegmentAllocator(int segmentSize, int minSegmentsToRetain, int maxSegments)
           
    {
        super(segmentSize, minSegmentsToRetain, maxSegments);
    }
    
    /*
    /**********************************************************************
    /* Abstract method implementations
    /**********************************************************************
     */
    
    protected LongsSegment _allocateSegment()
    {
        // can reuse a segment returned earlier?
        if (_reusableSegmentCount > 0) {
            LongsSegment segment = _firstReusableSegment;
            _firstReusableSegment = segment.getNext();
            ++_bufferOwnedSegmentCount; 
            --_reusableSegmentCount;
            return segment;
        }
        LongsSegment segment = new ArrayLongsSegment(_segmentSize);
        ++_bufferOwnedSegmentCount; 
        return segment;
    }
}
