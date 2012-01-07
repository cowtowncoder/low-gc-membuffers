package com.fasterxml.util.membuf.impl;

import com.fasterxml.util.membuf.*;
import com.fasterxml.util.membuf.base.BytesSegment;
import com.fasterxml.util.membuf.base.BytesSegmentAllocator;

/**
 * {@link SegmentAllocator} that allocates {@link ByteBufferBytesSegment}.
 */
public class ByteBufferBytesSegmentAllocator extends BytesSegmentAllocator
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
    
    public ByteBufferBytesSegmentAllocator(int segmentSize, int minSegmentsToRetain, int maxSegments,
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
    
    protected BytesSegment _allocateSegment()
    {
        // can reuse a segment returned earlier?
        if (_reusableSegmentCount > 0) {
            BytesSegment segment = _firstReusableSegment;
            _firstReusableSegment = segment.getNext();
            ++_bufferOwnedSegmentCount; 
            --_reusableSegmentCount;
            return segment;
        }
        BytesSegment segment = new ByteBufferBytesSegment(_segmentSize, _cfgAllocateNative);
        ++_bufferOwnedSegmentCount; 
        return segment;
    }
}
