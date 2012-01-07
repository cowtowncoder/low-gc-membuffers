package com.fasterxml.util.membuf.base;

import com.fasterxml.util.membuf.*;

/**
 * Intermediate mem buffer base class used for building type-specifc
 * "chunky" buffers, buffers where sequences of primitive values
 * are grouped into distinct entries such that all appends and reads
 * are entry by entry in FIFO order.
 */
public abstract class ChunkyMemBufferBase<S extends Segment<S>>
    extends MemBufferBase<S>
    implements ChunkyMemBuffer
{
    /*
    /**********************************************************************
    /* State
    /**********************************************************************
     */

    /**
     * Number of entries stored in this buffer.
     */
    protected int _entryCount;

    /*
    /**********************************************************************
    /* Read handling
    /**********************************************************************
     */

    /**
     * Length of the next entry, if known; -1 if not yet known.
     */
    protected int _nextEntryLength = -1;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */
    
    public ChunkyMemBufferBase(SegmentAllocator<S> allocator,
            int minSegmentsToAllocate, int maxSegmentsToAllocate,
            S initialSegments)
    {
        super(allocator, minSegmentsToAllocate, maxSegmentsToAllocate, initialSegments);
        _entryCount = 0;
        _totalPayloadLength = 0;
    }
    /*
    /**********************************************************************
    /* Public API, state changes
    /**********************************************************************
     */

    @Override
    public synchronized void clear()
    {
        _entryCount = 0;
        _nextEntryLength = -1;
        _clear();
    }

    // from base class:
    //public synchronized void close()
}
