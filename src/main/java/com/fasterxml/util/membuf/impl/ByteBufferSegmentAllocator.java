package com.fasterxml.util.membuf.impl;

import com.fasterxml.util.membuf.*;

/**
 * {@link SegmentAllocator} that allocates {@link ByteBufferSegment}.
 */
public class ByteBufferSegmentAllocator extends SegmentAllocator
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
    
    public ByteBufferSegmentAllocator(int segmentSize, int minSegmentsToRetain, int maxSegments,
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
        Segment segment = new ByteBufferSegment(_segmentSize, _cfgAllocateNative);
        ++_bufferOwnedSegmentCount; 
        return segment;
    }
}
