package com.fasterxml.util.membuf.base;

import com.fasterxml.util.membuf.*;

/**
 * Shared base class for all types of {@link MemBuffer} implementations
 * (regardless of underlying content type or streaming/chunked style)
 */
public abstract class MemBufferBase<S extends Segment<S>>
    implements MemBuffer // partial impl
{
    /*
    /**********************************************************************
    /* Basic configuration
    /**********************************************************************
     */

    /**
     * Object that is used for allocating physical segments, and to whom
     * segments are released after use.
     */
    protected final SegmentAllocator<S> _segmentAllocator;
    
    /**
     * Size of individual segments.
     */
    protected final int _segmentSize;
    
    /**
     * Smallest number of segments to allocate.
     * This defines the smallest
     * physical size of the queue, such that queue will never shrink beyond
     * this setting.
     * Lowest allowed minimum size is 2, since head and tail of the queue
     * must reside on different segments (to allow expansion)
     */
    protected final int _maxSegmentsForReuse;

    /**
     * Maximum number of segments to allocate.
     * This defines maximum physical size of the queue.
     */
    protected final int _maxSegmentsToAllocate;

    /*
    /**********************************************************************
    /* Storage
    /**********************************************************************
     */

    /**
     * Head refers to the segment in which appends are done, which is the
     * last segment allocated.
     * It may be same as <code>_tail</code>.
     * Note that this is the end of the logical chain starting from <code>_tail</code>.
     */
    protected S _head;

    /**
     * Tail refers to the segment from which read are done, which is the
     * oldest segment allocated.
     * It may be same as <code>_head</code>.
     * Can be used for traversing all in-use segments.
     */
    protected S _tail;

    /**
     * Number of segments reachable via linked list starting from
     * <code>_tail</code>
     */
    protected int _usedSegmentsCount;

    /**
     * Number of bytes stored in all appended entries.
     */
    protected long _totalPayloadLength;

    /*
    /**********************************************************************
    /* Simple segment reuse
    /**********************************************************************
     */

    /**
     * Most recently released segment that we hold on to for possible reuse.
     * Only up to {@link #_maxSegmentsForReuse} may be stored for reuse; others
     * will be handed back to the allocator.
     */
    protected S _firstFreeSegment;

    /**
     * Number of segments reachable via {@link #_firstFreeSegment};
     * less than equal to {@link #_maxSegmentsToAllocate}.
     */
    protected int _freeSegmentCount;
    
    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */
    
    protected MemBufferBase(SegmentAllocator<S> allocator,
            int minSegmentsToAllocate, int maxSegmentsToAllocate,
            S initialSegments)
    {
        _segmentAllocator = allocator;
        _segmentSize = allocator.getSegmentSize();
        _maxSegmentsForReuse = minSegmentsToAllocate;
        _maxSegmentsToAllocate = maxSegmentsToAllocate;
        // all but one of segments goes to the free list
        _firstFreeSegment = initialSegments.getNext();
        // and first one is used as both head and tail
        initialSegments.relink(null);
        _head = _tail = initialSegments;
        // also, better initialize initial segment for writing and reading
        _head.initForWriting();
        _head.initForReading();
        
        _usedSegmentsCount = 1;
        // Sanity checks? if yes, uncomment this...
        /*
            int count = count(_firstFreeSegment);
            if (count != _freeSegmentCount) {
                throw new IllegalStateException("Bad initial _freeSegmentCount ("+_freeSegmentCount+"): but only got "+count+" linked");
            }
        */
        _freeSegmentCount = minSegmentsToAllocate-1;
    }

    /*
    /**********************************************************************
    /* Extended API (for testing)
    /**********************************************************************
     */

    public SegmentAllocator<S> getAllocator() { return _segmentAllocator; }
    
    /*
    /**********************************************************************
    /* Public API, simple statistics (not data) accessors
    /**********************************************************************
     */

    @Override
    public synchronized int getSegmentCount() {
        return _usedSegmentsCount;
    }

    @Override
    public synchronized long getTotalPayloadLength()
    {
        return _totalPayloadLength + _peekedLength();
    }
    
    @Override
    public synchronized long getMaximumAvailableSpace()
    {
        if (_head == null) { // closed
            return -1L;
        }
        
        // First: how much room do we have in the current write segment?
        long space = _head.availableForAppend();
        // and how many more segments could we allocate?
        int canAllocate = (_maxSegmentsToAllocate - _usedSegmentsCount);

        if (canAllocate > 0) {
            space += (long) canAllocate * (long) _segmentSize;
        }
        return space;
    }

    /*
    /**********************************************************************
    /* Public API, state changes
    /**********************************************************************
     */

    //public synchronized void clear()

    @Override // from Closeable -- note, does NOT throw IOException
    public synchronized void close()
    {
        // first do regular cleanup
        clear();
        // then free the head/tail node as well
        _usedSegmentsCount = 0;
        _segmentAllocator.releaseSegment(_head);
        _head = _tail = null;
        // and any locally recycled buffers as well
        
        S seg = _firstFreeSegment;
        _firstFreeSegment = null;
        _freeSegmentCount = 0;
        
        while (seg != null) {
            S next = seg.getNext();
            _segmentAllocator.releaseSegment(seg);
            seg = next;
        }
    }

    /*
    /**********************************************************************
    /* Internal methods for sub-classes to use
    /**********************************************************************
     */

    /**
     * Method intended to be called from "clear()", after sub-class has done
     * its own cleanup. Not marked as synchronized as sub-class is expected
     * to do that if necessary.
     */
    protected void _clear()
    {
        if (_head == null) { // closed; nothing to do
            return;
        }
        // first: reset various counters (since this can't fail)
        _totalPayloadLength = 0L;
        _clearPeeked();
        
        // then free all segments except for head: note, may get an internal error:
        String error = null;
        while (_tail != _head) {
            error = _freeReadSegment(error);
        }
        // then re-init head/tail
        _tail.clear();
        // and finally, indicate error, if any
        if (error != null) { // sanity check after everything else
            throw new IllegalStateException(error);
        }
    }
    
    /*
    /**********************************************************************
    /* Abstract methods for sub-classes to implement
    /**********************************************************************
     */

    protected abstract String _freeReadSegment(String prevError);

    protected abstract void _reportClosed();

    protected abstract void _clearPeeked();
    
    protected abstract int _peekedLength();

}
