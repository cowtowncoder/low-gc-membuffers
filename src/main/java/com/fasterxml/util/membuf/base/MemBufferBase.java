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
    /* Support for synchronization
    /**********************************************************************
     */

    /**
     * Number of threads currently blocked and waiting for more data.
     * Used for reducing unnecessary calls to 'this.notifyAll();'.
     *<p>
     * Since it is only updated from synchronized blocks we do not
     * need to use <code>AtomicInteger</code> (or mark volatile).
     */
    private int _readBlockedCount;
    
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

    protected MemBufferBase(MemBufferBase<S> src)
    {
        _segmentAllocator = src._segmentAllocator;
        _segmentSize = src._segmentSize;
        _maxSegmentsForReuse = src._maxSegmentsForReuse;
        _maxSegmentsToAllocate = src._maxSegmentsToAllocate;

        _head = src._head;
        _tail = src._tail;
        _usedSegmentsCount = src._usedSegmentsCount;
        _totalPayloadLength = src._totalPayloadLength;

        _firstFreeSegment = src._firstFreeSegment;
        _freeSegmentCount = src._freeSegmentCount;
        
        _readBlockedCount = src._readBlockedCount;
    }
    
    /*
    /**********************************************************************
    /* Public API, waiting
    /**********************************************************************
     */

    @Override
    public final synchronized void waitUntilNotEmpty() throws InterruptedException
    {
        if (_head == null) {
            _reportClosed();
        }
        if (isEmpty()) {
            this.wait();
        }
    }

    @Override
    public final synchronized void waitUntilNotEmpty(long maxWaitMsecs) throws InterruptedException
    {
        if (_head == null) {
            _reportClosed();
        }
        if (isEmpty()) {
            this.wait(maxWaitMsecs);
        }
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
    public synchronized final void close()
    {
        // first do regular cleanup
        clear();
        // then free the head/tail node as well
        _usedSegmentsCount = 0;
        // 24-Apr-2013, tatu: As per #16, must ensure proper cleaning
        _head.markFree();
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
        
        // one more thing: wake up thread(s) that are blocked (if any)
        this.notifyAll();
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

    /**
     * Helper method called when the current tail segment has been completely
     * read, and we want to free or reuse it and start reading the next
     * segment.
     * Since throwing exceptions from this method could lead to corruption,
     * we will only return error indicator for any problems.
     */
    protected final String _freeReadSegment(String prevError)
    {
        S old = _tail;
        S next = old.finishReading();
        --_usedSegmentsCount;
        _tail = next.initForReading();

        // how about freed segment? reuse?
        if ((_usedSegmentsCount + _freeSegmentCount) < _maxSegmentsForReuse) {
            if (_firstFreeSegment == null) {
                // sanity check: should never occur
                if (_freeSegmentCount != 0) {
                    if (prevError == null) {
                        prevError = "_firstFreeSegment null; count "+_freeSegmentCount+" (should be 0)";
                    }
                    // but has happened in the past, so fix even then
                    _freeSegmentCount = 0;                    
                }
                // this is enough; old.next has been set to null already:
                _firstFreeSegment = old;
                _freeSegmentCount = 1;
            } else {
                _firstFreeSegment = old.relink(_firstFreeSegment);
                ++_freeSegmentCount;
            }
        } else { // if no reuse, see if allocator can share
            // 24-Apr-2013, tatu: As per #16, must ensure proper cleaning
            // NOTE: not sure if it is required here; is when closing. But given
            // severity of problem if encountered, better safe than sorry.
            old.markFree();
            _segmentAllocator.releaseSegment(old);
        }
        return prevError;
    }

    /**
     * Helper method for reusing a segment from free-segments list.
     * Caller must guarantee there is such a segment available; this is
     * done in advance to achieve atomicity of multi-segment-allocation.
     */
    protected final S _reuseFree()
    {
        S freeSeg = _firstFreeSegment;
        if (freeSeg == null) { // sanity check
            throw new IllegalStateException("Internal error: no free segments available");
        }
        
//int oldCount = count(_firstFreeSegment);
        
        _firstFreeSegment = freeSeg.getNext();
        --_freeSegmentCount;        
        _head = freeSeg;
        ++_usedSegmentsCount;

// optional sanity check, if we are tracking hard-to-find bugs...
/*
int count = count(_firstFreeSegment);
System.err.print("[r="+oldCount+"->"+_freeSegmentCount+"(c="+count+")/u="+_usedSegmentsCount+"]");
if (count != _freeSegmentCount) {
 System.err.println("ERROR: free seg "+_freeSegmentCount+"; but saw "+count+" actual!");
}
*/
        return freeSeg;
    }
    
    /**
     * Method sub-classes call when the current thread should block
     * until more data is available.
     * Note that caller MUST have lock on 'this', that is call must
     * come from within synchronized block.
     */
    protected final void _waitForData() throws InterruptedException {
        ++_readBlockedCount; // ok since we are calling from sync block
        this.wait();
    }

    /**
     * Method sub-classes call when the current thread should block
     * up to specified time interval, or until more data is available,
     * whichever occurs sooner.
     * Note that caller MUST have lock on 'this', that is call must
     * come from within synchronized block.
     */
    protected final void _waitForData(long timeoutMsecs) throws InterruptedException {
        ++_readBlockedCount;
        this.wait(timeoutMsecs);
    }
    
    /**
     * Method sub-classes call when they need to wake up any threads
     * that are blocked on trying to read content: typically this occurs
     * when adding entry or data to an empty buffer.
     * Note that caller MUST have lock on 'this', that is call must
     * come from within synchronized block.
     */
    protected final void _wakeBlockedReaders() {
        if (_readBlockedCount != 0) {
            _readBlockedCount = 0;
            this.notifyAll();
        }
    }
    
    /* Helper method called to throw an exception when an active method
     * is called after buffer has been closed.
     */
    protected final void _reportClosed() {
        throw new IllegalStateException("MemBuffer instance closed, can not use");
    }
    
    /*
    /**********************************************************************
    /* Abstract methods for sub-classes to implement
    /**********************************************************************
     */

    protected abstract void _clearPeeked();
    
    protected abstract int _peekedLength();

}
