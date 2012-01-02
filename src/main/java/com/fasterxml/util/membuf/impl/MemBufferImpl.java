package com.fasterxml.util.membuf.impl;

import com.fasterxml.util.membuf.*;

/**
 * Default {@link MemBuffer} implementation, which implements fully
 * synchronized access.
 */
public class MemBufferImpl extends MemBuffer
{
    private final static byte[] EMPTY_PAYLOAD = new byte[0];
    
    /*
    /**********************************************************************
    /* Basic configuration
    /**********************************************************************
     */

    /**
     * Object that is used for allocating physical segments, and to whom
     * segments are released after use.
     */
    protected final SegmentAllocator _segmentAllocator;
    
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
    protected Segment _head;

    /**
     * Tail refers to the segment from which read are done, which is the
     * oldest segment allocated.
     * It may be same as <code>_head</code>.
     * Can be used for traversing all in-use segments.
     */
    protected Segment _tail;

    /**
     * Number of segments reachable via linked list starting from
     * <code>_tail</code>
     */
    protected int _usedSegmentsCount;

    /**
     * Number of entries stored in this buffer.
     */
    protected int _entryCount;

    /**
     * Number of bytes stored in all appended entries.
     */
    protected long _totalPayloadLength;

    
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
    /* Simple segment reuse
    /**********************************************************************
     */

    /**
     * Most recently released segment that we hold on to for possible reuse.
     * Only up to {@link #_maxSegmentsForReuse} may be stored for reuse; others
     * will be handed back to the allocator.
     */
    protected Segment _firstFreeSegment;

    /**
     * Number of segments reachable via {@link #_firstFreeSegment};
     * less than equal to {@link #_maxSegmentsToAllocate}.
     */
    protected int _freeSegmentCount;

    /*
    /**********************************************************************
    /* Other
    /**********************************************************************
     */

    /**
     * Length prefix is between one and five bytes long, to encode
     * int32 as VInt (most-significant-byte first),
     * where last byte is indicated by set sign bit.
     */
    protected final byte[] _lengthPrefixBuffer = new byte[5];
    
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
    public MemBufferImpl(SegmentAllocator allocator,
            int minSegmentsToAllocate, int maxSegmentsToAllocate,
            Segment initialSegments)
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
        _freeSegmentCount = minSegmentsToAllocate-1; // should we verify this?
        _entryCount = 0;
        _totalPayloadLength = 0;
    }

    /*
    /**********************************************************************
    /* Public API, simple statistics (not data) accessors
    /**********************************************************************
     */

    @Override
    public synchronized int getEntryCount() {
        return _entryCount;
    }

    @Override
    public synchronized boolean isEmpty() {
        return (_entryCount == 0);
    }
    
    @Override
    public synchronized int getSegmentCount() {
        return _usedSegmentsCount;
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

    @Override
    public synchronized long getTotalPayloadLength()
    {
        return _totalPayloadLength;
    }
    
    /*
    /**********************************************************************
    /* Public API, state changes
    /**********************************************************************
     */

    @Override
    public synchronized void clear()
    {
        if (_head == null) { // closed; nothing to do
            return;
        }
        
        // first, free all segments except for head
        while (_tail != _head) {
            _freeReadSegment();
        }
        // then re-init head/tail
        _tail.clear();
        
        // and reset various counters
        _entryCount = 0;
        _totalPayloadLength = 0L;
        _nextEntryLength = -1;
    }

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
        Segment seg;
        while ((seg = _firstFreeSegment) != null) {
            _firstFreeSegment = seg.getNext();
            _segmentAllocator.releaseSegment(seg);
        }
        _freeSegmentCount = 0;
    }

    /*
    /**********************************************************************
    /* Public API, write (append)
    /**********************************************************************
     */

    @Override
    public final void appendEntry(byte[] data) {
        appendEntry(data, 0, data.length);
    }

    @Override
    public void appendEntry(byte[] data, int dataOffset, int dataLength)
    {
        if (!tryAppendEntry(data, dataOffset, dataLength)) {
            throw new IllegalStateException("Not enough room in buffer to append entry of "+dataLength
                    +" (can't allocate enough new segments)");
        }
    }

    @Override
    public final boolean tryAppendEntry(byte[] data) {
        return tryAppendEntry(data, 0, data.length);
    }

    @Override
    public synchronized boolean tryAppendEntry(byte[] data, int dataOffset, int dataLength)
    {
        if (_head == null) {
            _reportClosed();
        }
        
        // first, calculate total size (length prefix + payload)
        int prefixLength = _calcLengthPrefix(_lengthPrefixBuffer, dataLength);
        int freeInCurrent = _head.availableForAppend();
        int totalLength = (dataLength + prefixLength);
        // First, simple case: can fit it in the current buffer?
        if (freeInCurrent >= totalLength) {
            _head.append(_lengthPrefixBuffer, 0, prefixLength);
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
                // if we are, let's try allocate
                Segment newFree = _segmentAllocator.allocateSegments(segmentsToAlloc, _firstFreeSegment);
                if (newFree == null) {
                    return false;
                }
                _freeSegmentCount += segmentsToAlloc;
                _firstFreeSegment = newFree;
            }
    
            // and if we got this far, it's just simple matter of writing pieces into segments
            // first length prefix
            _doAppendChunked(_lengthPrefixBuffer, 0, prefixLength);
            _doAppendChunked(data, dataOffset, dataLength);
        }
        _totalPayloadLength += dataLength;        
        if (++_entryCount == 1) {
            this.notifyAll();
        }
        return true;
    }

    protected void _doAppendChunked(byte[] buffer, int offset, int length)
    {
        if (length < 1) {
            return;
        }
        Segment seg = _head;
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
            Segment newSeg = _reuseFree().initForWriting();
            seg.relink(newSeg);
            _head = seg = newSeg;
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
    public synchronized byte[] getNextEntry() throws InterruptedException
    {
        if (_head == null) {
            _reportClosed();
        }
        // first: must have something to return
        while (_entryCount == 0) {
            this.wait();
        }
        return _doGetNext();
    }

    @Override
    public synchronized byte[] getNextEntryIfAvailable()
    {
        if (_head == null) {
            _reportClosed();
        }
        if (_entryCount == 0) {
            return null;
        }
        return _doGetNext();
    }
    
    @Override
    public synchronized byte[] getNextEntry(long timeoutMsecs) throws InterruptedException
    {
        if (_head == null) {
            _reportClosed();
        }
        if (_entryCount > 0) {
            return _doGetNext();
        }
        long now = System.currentTimeMillis();
        long end = now + timeoutMsecs;
        while (now < end) {
            wait(end - now);
            if (_entryCount > 0) {
                return _doGetNext();
            }
            now = System.currentTimeMillis();
        }
        return null;
    }

    @Override
    public synchronized int readNextEntry(byte[] buffer, int offset) throws InterruptedException
    {
        if (_head == null) {
            _reportClosed();
        }
        // first: must have something to return
        while (_entryCount == 0) {
            this.wait();
        }
        return _doGetNext(buffer, offset);
    }

    @Override
    public synchronized int readNextEntryIfAvailable(byte[] buffer, int offset)
    {
        if (_head == null) {
            _reportClosed();
        }
        if (_entryCount == 0) {
            return Integer.MIN_VALUE;
        }
        return _doGetNext(buffer, offset);
    }

    @Override
    public synchronized int readNextEntry(long timeoutMsecs, byte[] buffer, int offset)
        throws InterruptedException
    {
        if (_head == null) {
            _reportClosed();
        }
        if (_entryCount > 0) {
            return _doGetNext(buffer, offset);
        }
        long now = System.currentTimeMillis();
        long end = now + timeoutMsecs;
        while (now < end) {
            wait(end - now);
            if (_entryCount > 0) {
                return _doGetNext(buffer, offset);
            }
            now = System.currentTimeMillis();
        }
        return Integer.MIN_VALUE;
    }
    
    /*
    /**********************************************************************
    /* Public API, read-like access: skipping, wait-for-next
    /**********************************************************************
     */
    
    @Override
    public synchronized int skipNextEntry()
    {
        if (_head == null) {
            _reportClosed();
        }
        if (_entryCount < 1) {
            return -1;
        }

        final int segLen = getNextEntryLength();
        // ensure lengthh indicator gets reset for chunk after this one
        _nextEntryLength = -1;
        // and reduce entry count as well
        --_entryCount;
        _totalPayloadLength -= segLen;

        // a trivial case; marker entry (no payload)
        int remaining = segLen;
        while (remaining > 0) {
            remaining -= _tail.skip(remaining);
            if (remaining == 0) { // all skipped?
                break;
            }
            _freeReadSegment();
        }
        return segLen;
        
    }
    
    @Override
    public synchronized void waitForNextEntry() throws InterruptedException
    {
        if (_head == null) {
            _reportClosed();
        }
        if (_entryCount == 0) {
            this.wait();
        }
    }

    @Override
    public synchronized void waitForNextEntry(long maxWaitMsecs) throws InterruptedException
    {
        if (_head == null) {
            _reportClosed();
        }
        if (_entryCount == 0) {
            this.wait(maxWaitMsecs);
        }
    }    
    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */

    /* Helper method for reusing a segment from free-segments list.
     * Caller must guarantee there is such a segment available; this is
     * done in advance to achieve atomicity of multi-segment-allocation.
     */
    protected final Segment _reuseFree()
    {
        Segment freeSeg = _firstFreeSegment;
        if (freeSeg == null) { // sanity check
            throw new IllegalStateException("Internal error: no free segments available");
        }
        _firstFreeSegment = freeSeg.getNext();
        --_freeSegmentCount;
        _head = freeSeg;
        ++_usedSegmentsCount;
        return freeSeg;
    }
    
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

        // otherwise we got negated version of partial length, so find what we got:
        len = -len - 1;

        // and move to read the next segment;
        _freeReadSegment();
        // and then read enough data to figure out length:
        return _tail.readSplitLength(len);
    }

    private byte[] _doGetNext()
    {
        int segLen = getNextEntryLength();

        // start with result allocation, so that possible OOME does not corrupt state
        byte[] result = new byte[segLen];
        
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

    private int _doGetNext(byte[] buffer, int offset)
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
    protected void _doReadChunked(byte[] buffer, int offset, int length)
    {
        while (true) {
            int actual = _tail.tryRead(buffer, offset, length);
            offset += actual;
            length -= actual;
            if (length == 0) { // complete, can leave
                return;
            }
            _freeReadSegment();
        }
    }

    /**
     * Helper method called when the current tail segment has been completely
     * read, and we want to free or reuse it and start reading the next
     * segment.
     */
    private void _freeReadSegment()
    {
        Segment old = _tail;
        Segment next = old.finishReading();
        --_usedSegmentsCount;
        _tail = next.initForReading();
        // how about freed segment? reuse?
        if ((_usedSegmentsCount + _freeSegmentCount) < _maxSegmentsForReuse) {
            if (_firstFreeSegment == null) {
                // sanity check
                if (_freeSegmentCount != 0) {
                    throw new IllegalStateException("_firstFreeSegment null; count "+_freeSegmentCount+" (should be 0)");
                }
                // this is enough; old.next has been set to null already:
                _firstFreeSegment = old;
                _freeSegmentCount = 1;
            } else {
                _firstFreeSegment = _firstFreeSegment.relink(old);
                ++_freeSegmentCount;
            }
        } else { // if no reuse, see if allocator can share
            _segmentAllocator.releaseSegment(old);
        }
    }

    private int _calcLengthPrefix(byte[] buffer, int length)
    {
        if (length < 0) {
            throw new IllegalArgumentException("Negative length: "+length);
        }
        if (length <= 0x7F) {
            buffer[0] = (byte) (length | 0x80);
            return 1;
        }
        if (length <= 0x3FFF) {
            buffer[0] = (byte) ((length >> 7) & 0x7F);
            buffer[1] = (byte) ((length & 0x7f) | 0x80);
            return 2;
        }
        if (length <= 0x1FFFFF) {
            buffer[0] = (byte) ((length >> 14) & 0x7F);
            buffer[1] = (byte) ((length >> 7) & 0x7F);
            buffer[2] = (byte) ((length & 0x7f) | 0x80);
            return 3;
        }
        if (length <= 0x0FFFFFFF) {
            buffer[0] = (byte) ((length >> 21) & 0x7F);
            buffer[1] = (byte) ((length >> 14) & 0x7F);
            buffer[2] = (byte) ((length >> 7) & 0x7F);
            buffer[3] = (byte) ((length & 0x7f) | 0x80);
            return 4;
        }
        buffer[0] = (byte) ((length >> 28) & 0x7F);
        buffer[1] = (byte) ((length >> 21) & 0x7F);
        buffer[2] = (byte) ((length >> 14) & 0x7F);
        buffer[3] = (byte) ((length >> 7) & 0x7F);
        buffer[4] = (byte) ((length & 0x7f) | 0x80);
        return 5;
    }

    /* Helper method called to throw an exception when an active method
     * is called after buffer has been closed.
     */
    protected void _reportClosed() {
        throw new IllegalStateException("MemBuffer instance closed, can not use");
    }
}
