package com.fasterxml.util.membuf.base;

import com.fasterxml.util.membuf.*;

/**
 * Intermediate mem buffer base class used for building type-specifc
 * "streamy" buffers.
 */
public abstract class StreamyMemBufferBase<S extends Segment<S>>
    extends MemBufferBase<S>
    implements StreamyMemBuffer
{
    /*
    /**********************************************************************
    /* State
    /**********************************************************************
     */

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    public StreamyMemBufferBase(SegmentAllocator<S> allocator,
            int minSegmentsToAllocate, int maxSegmentsToAllocate,
            S initialSegments)
    {
        super(allocator, minSegmentsToAllocate, maxSegmentsToAllocate, initialSegments);
    }
    /*
    /**********************************************************************
    /* Public API, state changes
    /**********************************************************************
     */
    
    @Override
    public synchronized void clear()
    {
        _clear();
    }
    
    // from base class:
    //public synchronized void close()
}
