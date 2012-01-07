package com.fasterxml.util.membuf.impl;

import com.fasterxml.util.membuf.base.*;

public class ByteBufferLongsSegmentAllocator extends LongsSegmentAllocator
{
    /*
    /**********************************************************************
    /* Configuration
    /**********************************************************************
     */

    protected final boolean _cfgAllocateNative;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */
    
    public ByteBufferLongsSegmentAllocator(int segmentSize, int minSegmentsToRetain, int maxSegments,
            boolean allocateNativeBuffers)
           
    {
        super(segmentSize, minSegmentsToRetain, maxSegments);
        _cfgAllocateNative = allocateNativeBuffers;
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
        LongsSegment segment = new ByteBufferLongsSegment(_segmentSize, _cfgAllocateNative);
        ++_bufferOwnedSegmentCount; 
        return segment;
    }
}
