package com.fasterxml.util.membuf.impl;

import com.fasterxml.util.membuf.Segment;
import com.fasterxml.util.membuf.SegmentAllocator;

/**
 * {@link SegmentAllocator} implementation that allocates
 * {@link BytesArraySegment}s, which are simple byte array backed segments.
 */
public class BytesArraySegmentAllocator extends BytesSegmentAllocator
{
    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */
    
    public BytesArraySegmentAllocator(int segmentSize, int minSegmentsToRetain, int maxSegments)
           
    {
        super(segmentSize, minSegmentsToRetain, maxSegments);
    }
    
    /*
    /**********************************************************************
    /* Abstract method implementations
    /**********************************************************************
     */
    
    protected Segment _allocateSegment()
    {
        // can reuse a segment returned earlier?
        if (_reusableSegmentCount > 0) {
            Segment segment = _firstReusableSegment;
            _firstReusableSegment = segment.getNext();
            ++_bufferOwnedSegmentCount; 
            --_reusableSegmentCount;
            return segment;
        }
        Segment segment = new BytesArraySegment(_segmentSize);
        ++_bufferOwnedSegmentCount; 
        return segment;
    }
}
