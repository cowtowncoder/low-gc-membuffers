package com.fasterxml.util.membuf.impl;

import com.fasterxml.util.membuf.*;
import com.fasterxml.util.membuf.base.LongsSegment;

/**
 * {@link MemBuffer} implementation used for storing entries
 * that are sequences of long values.
 *<p>
 * Access to queue is fully synchronized -- meaning that all methods are
 * synchronized by implementations as necessary, and caller should not need
 * to use external synchronization -- since parts will have to be anyway
 * (updating of stats, pointers), and since all real-world use cases will
 * need some level of synchronization anyway, even with just single producer
 * and consumer. If it turns out that there are bottlenecks that could be
 * avoided with more granular (or external) locking, this design can be
 * revisited.
 *<p>
 * Note that if instances are discarded, they <b>MUST</b> be closed:
 * finalize() method is not implemented since it is both somewhat unreliable
 * (i.e. should not be counted on) and can add overhead for GC processing.
 */
public class ChunkyLongsMemBufferImpl extends ChunkyLongsMemBuffer
{
    private final static long[] EMPTY_PAYLOAD = new long[0];

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    /**
     * @param allocator Allocator used for allocating underlying segments
     * @param minSegmentsToAllocate Maximum number of segments to hold on to
     *   (for reuse) after being released. Increases minimum memory usage
     *   but can improve performance by avoiding unnecessary re-allocation;
     *   and also guarantees that buffer always has at least this much
     *   storage capacity.
     * @param maxSegmentsToAllocate Maximum number of segments that can be
     *   allocated for this buffer: limits maximum capacity and memory usage
     * @param initialSegments Chain of pre-allocated segments, containing
     *   <code>_maxSegmentsForReuse</code> segments that are allocated to ensure
     *   that there is always specified minimum capacity available
     */
    public ChunkyLongsMemBufferImpl(SegmentAllocator<LongsSegment> allocator,
            int minSegmentsToAllocate, int maxSegmentsToAllocate,
            LongsSegment initialSegments)
    {
        super(allocator, minSegmentsToAllocate, maxSegmentsToAllocate, initialSegments);
    }
    

    /*
    /**********************************************************************
    /* Public API, write (append)
    /**********************************************************************
     */

    // from base class:
    //public final void appendEntry(long[] data);
    //public void appendEntry(long[] data, int dataOffset, int dataLength);
    //public final boolean tryAppendEntry(long[] data);

    @Override
    public synchronized boolean tryAppendEntry(long[] data, int dataOffset, int dataLength)
    {
        if (_head == null) {
            _reportClosed();
        }
        // first, calculate total size (length prefix + payload)
        int freeInCurrent = _head.availableForAppend();
        int totalLength = (dataLength + 1);
        // First, simple case: can fit it in the current buffer?
        if (freeInCurrent >= totalLength) {
            if (!_head.tryAppend(dataLength)) { // sanity check, should never occur as per earlier test
                throw new IllegalStateException();
            }
            _head.append(data, dataOffset, dataLength);
        } else {
            // if not, must check whether we could allocate enough segments to fit in
            int neededSegments = ((totalLength - freeInCurrent) + (_segmentSize-1)) / _segmentSize;
    
            // Which may need reusing local segments, or allocating new ones via allocates
            int segmentsToAlloc = neededSegments - _freeSegmentCount;
            if (segmentsToAlloc > 0) { // nope: need more
                // ok, but are allowed to grow that big?
                if ((_usedSegmentsCount + _freeSegmentCount + segmentsToAlloc) > _maxSegmentsToAllocate) {
                    return false;
                }
                // if we are, let's try allocate: will be added to "free" segments first, then used
                LongsSegment newFree = _segmentAllocator.allocateSegments(segmentsToAlloc, _firstFreeSegment);
                if (newFree == null) {
                    return false;
                }
                _freeSegmentCount += segmentsToAlloc;
                _firstFreeSegment = newFree;
            }
    
            // and if we got this far, it's just simple matter of writing pieces into segments
            _doAppendChunked(data, dataOffset, dataLength);
        }
        _totalPayloadLength += dataLength;        
        if (++_entryCount == 1) {
            this.notifyAll();
        }
        return true;
    }

    protected void _doAppendChunked(long[] buffer, int offset, int length)
    {
        // first: append length prefix
        if (!_head.tryAppend(length)) {
            final LongsSegment seg = _head;
            seg.finishWriting();
            // and allocate, init-for-writing new one:
            LongsSegment newSeg = _reuseFree().initForWriting();
            seg.relink(newSeg);
            _head = newSeg;
            if (!_head.tryAppend(length)) {
                throw new IllegalStateException();
            }
        }
        // then payload
        if (length > 0) {
            LongsSegment seg = _head;
            while (true) {
                int actual = seg.tryAppend(buffer, offset, length);
                offset += actual;
                length -= actual;
                if (length == 0) { // complete, can leave
                    return;
                }
                // otherwise, need another segment, so complete current write
                seg.finishWriting();
                // and allocate, init-for-writing new one:
                LongsSegment newSeg = _reuseFree().initForWriting();
                seg.relink(newSeg);
                _head = seg = newSeg;
            }
        }
    }

    /*
    /**********************************************************************
    /* Public API, reading
    /**********************************************************************
     */

    @Override
    public synchronized int getNextEntryLength()
    {
        if (_head == null) {
            _reportClosed();
        }
        if (_peekedEntry != null) {
            return _peekedEntry.length;
        }
        int len = _nextEntryLength;
        if (len < 0) { // need to read it?
            if (_entryCount == 0) { // but can only read if something is actually available
                return -1;
            }
            _nextEntryLength = len = _readEntryLength();
        }
        return len;
    }

    @Override
    public synchronized long[] getNextEntry() throws InterruptedException
    {
        if (_head == null) {
            _reportClosed();
        }
        if (_peekedEntry != null) {
            long[] result = _peekedEntry;
            _peekedEntry = null;
            return result;
        }        
        // first: must have something to return
        while (_entryCount == 0) {
            _waitForData();
        }
        return _doGetNext();
    }

    @Override
    public synchronized long[] getNextEntryIfAvailable()
    {
        if (_head == null) {
            _reportClosed();
        }
        if (_peekedEntry != null) {
            long[] result = _peekedEntry;
            _peekedEntry = null;
            return result;
        }        
        if (_entryCount == 0) {
            return null;
        }
        return _doGetNext();
    }
    
    @Override
    public synchronized long[] getNextEntry(long timeoutMsecs) throws InterruptedException
    {
        if (_head == null) {
            _reportClosed();
        }
        if (_peekedEntry != null) {
            long[] result = _peekedEntry;
            _peekedEntry = null;
            return result;
        }        
        if (_entryCount > 0) {
            return _doGetNext();
        }
        long now = System.currentTimeMillis();
        long end = now + timeoutMsecs;
        while (now < end) {
            _waitForData(end - now);
            if (_entryCount > 0) {
                return _doGetNext();
            }
            now = System.currentTimeMillis();
        }
        return null;
    }

    @Override
    public synchronized int readNextEntry(long[] buffer, int offset) throws InterruptedException
    {
        if (_head == null) {
            _reportClosed();
        }
        if (_peekedEntry != null) {
            return _doReadPeekedEntry(buffer, offset);
        }        
        
        // first: must have something to return
        while (_entryCount == 0) {
            _waitForData();
        }
        return _doReadNext(buffer, offset);
    }

    @Override
    public synchronized int readNextEntryIfAvailable(long[] buffer, int offset)
    {
        if (_head == null) {
            _reportClosed();
        }
        if (_entryCount == 0) {
            return Integer.MIN_VALUE;
        }
        return _doReadNext(buffer, offset);
    }

    @Override
    public synchronized int readNextEntry(long timeoutMsecs, long[] buffer, int offset)
        throws InterruptedException
    {
        if (_head == null) {
            _reportClosed();
        }
        if (_entryCount > 0) {
            return _doReadNext(buffer, offset);
        }
        long now = System.currentTimeMillis();
        long end = now + timeoutMsecs;
        while (now < end) {
            _waitForData(end - now);
            if (_entryCount > 0) {
                return _doReadNext(buffer, offset);
            }
            now = System.currentTimeMillis();
        }
        return Integer.MIN_VALUE;
    }

    /*
    /**********************************************************************
    /* Public API, peeking
    /**********************************************************************
     */
    
    @Override
    public synchronized long[] peekNextEntry()
    {
        if (_head == null) {
            _reportClosed();
        }
        if (_peekedEntry == null) {
            if (_entryCount < 1) {
                return null;
            }
            _peekedEntry = _doGetNext();
        }
        return _peekedEntry;
    }
    
    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */
    
    /* Helper method used to read length of next segment.
     * Caller must ensure that there is at least one more segment
     * to read.
     */
    private int _readEntryLength()
    {
        // see how much of length prefix we can read
        int len = _tail.readLength();
        if (len >= 0) { // all!
            return len;
        }
        // otherwise segment was empty, move on
        // and move to read the next segment;
        String error = _freeReadSegment(null);
        if (error != null) {
            throw new IllegalStateException(error);
        }
        // and then read enough data to figure out length:
        len = _tail.readLength();
        if (len < 0) {
            throw new IllegalStateException("Failed to read next segment length");
        }
        return len;
    }

    private long[] _doGetNext()
    {
        int segLen = getNextEntryLength();

        // start with result allocation, so that possible OOME does not corrupt state
        long[] result = new long[segLen];
        
        // but ensure that it gets reset for chunk after this one
        _nextEntryLength = -1;
        // and reduce entry count as well
        --_entryCount;
        _totalPayloadLength -= segLen;

        // a trivial case; marker entry (no payload)
        if (segLen == 0) {
            return EMPTY_PAYLOAD;
        }

        // ok: simple case; all data available from within current segment
        int avail = _tail.availableForReading();
        if (avail >= segLen) {
            _tail.read(result, 0, segLen);
        } else {
            // but if not we'll just do the segment read...
            _doReadChunked(result, 0, segLen);
        }
        return result;
    }

    private int _doReadNext(long[] buffer, int offset)
    {
        int end = buffer.length;
        
        if (offset >= end || offset < 0) {
            throw new IllegalArgumentException("Illegal offset ("+offset+"): allowed values [0, "+end+"[");
        }
        final int maxLen = end - offset;
        final int segLen = getNextEntryLength();

        // not enough room?
        if (segLen > maxLen) {
            return -segLen;
        }
        
        // but ensure that it gets reset for chunk after this one
        _nextEntryLength = -1;
        // and reduce entry count as well
        --_entryCount;
        _totalPayloadLength -= segLen;

        // a trivial case; marker entry (no payload)
        if (segLen == 0) {
            return 0;
        }

        // ok: simple case; all data available from within current segment
        int avail = _tail.availableForReading();
        if (avail >= segLen) {
            _tail.read(buffer, offset, segLen);
        } else {
            // but if not we'll just do the segment read...
            _doReadChunked(buffer, offset, segLen);
        }
        return segLen;
    }

    /**
     * Helper method that handles append when contents may need to be split
     * across multiple segments.
     */
    protected void _doReadChunked(long[] buffer, int offset, int length)
    {
        String error = null;
        while (true) {
            int actual = _tail.tryRead(buffer, offset, length);
            offset += actual;
            length -= actual;
            if (length == 0) { // complete, can leave
                break;
            }
            error = _freeReadSegment(error);
        }
        if (error != null) {
            throw new IllegalStateException(error);
        }
    }

    private int _doReadPeekedEntry(long[] buffer, int offset)
    {
        int end = buffer.length;
        if (offset >= end || offset < 0) {
            throw new IllegalArgumentException("Illegal offset ("+offset+"): allowed values [0, "+end+"[");
        }
        final int maxLen = end - offset;
        final int segLen = _peekedEntry.length;

        // not enough room?
        if (segLen > maxLen) {
            return -segLen;
        }
        if (segLen > 0) {
            System.arraycopy(_peekedEntry, 0, buffer, offset, segLen);
        }
        _peekedEntry = null;
        return segLen;
    }
}
