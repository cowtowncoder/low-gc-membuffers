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
    /* Life-cycle
    /**********************************************************************
     */

    public StreamyMemBufferBase(SegmentAllocator<S> allocator,
            int minSegmentsToAllocate, int maxSegmentsToAllocate,
            S initialSegments)
    {
        super(allocator, minSegmentsToAllocate, maxSegmentsToAllocate, initialSegments);
    }

    protected StreamyMemBufferBase(StreamyMemBufferBase<S> src) {
        super(src);
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

    /*
    /**********************************************************************
    /* Public API, skipping
    /**********************************************************************
     */
    
    @Override
    public synchronized final int skip(int skipCount)
    {
        if (_head == null) {
            _reportClosed();
        }
        if (skipCount > _totalPayloadLength) {
            skipCount = (int) _totalPayloadLength;
        }
        int remaining = skipCount;
        String error = null;
        if (remaining > 0) {
            while (true) {
                int count = _tail.skip(remaining);
                remaining -= count;
                _totalPayloadLength -= count;
                if (remaining == 0) { // all skipped?
                    break;
                }
                error = _freeReadSegment(error);
            }
        }
        if (error != null) {
            throw new IllegalStateException(error);
        }
        return skipCount;
    }

}
