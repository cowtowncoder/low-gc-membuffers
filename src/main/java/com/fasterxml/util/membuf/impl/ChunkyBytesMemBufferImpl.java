package com.fasterxml.util.membuf.impl;

import com.fasterxml.util.membuf.*;
import com.fasterxml.util.membuf.base.BytesSegment;

/**
 * {@link ChunkyBytesMemBuffer} implementation used for storing entries
 * that are sequences of byte values, and are stored as distinct entries
 * with definite size (as per {@link ChunkyMemBuffer}).
 * This means that entries that are read in order they were appended
 * and have exact same contents (including length).
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
public class ChunkyBytesMemBufferImpl extends ChunkyBytesMemBuffer
{
    private final static byte[] EMPTY_PAYLOAD = new byte[0];
    
    /*
    /**********************************************************************
    /* Temporary buffering
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
    public ChunkyBytesMemBufferImpl(SegmentAllocator<BytesSegment> allocator,
            int minSegmentsToAllocate, int maxSegmentsToAllocate,
            BytesSegment initialSegments)
    {
        super(allocator, minSegmentsToAllocate, maxSegmentsToAllocate, initialSegments);
    }    

    /**
     * Copy-constructor most useful for sub-classes used for wrapping
     * (usually using {@link MemBufferDecorator}).
     */
    protected ChunkyBytesMemBufferImpl(ChunkyBytesMemBuffer src) {
        super(src);
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
                // if we are, let's try allocate: will be added to "free" segments first, then used
                BytesSegment newFree = _segmentAllocator.allocateSegments(segmentsToAlloc, _firstFreeSegment);
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
        BytesSegment seg = _head;
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
            BytesSegment newSeg = _reuseFree().initForWriting();
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
    public synchronized byte[] getNextEntry() throws InterruptedException
    {
        if (_head == null) {
            _reportClosed();
        }
        if (_peekedEntry != null) {
            byte[] result = _peekedEntry;
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
    public synchronized byte[] getNextEntryIfAvailable()
    {
        if (_head == null) {
            _reportClosed();
        }
        if (_peekedEntry != null) {
            byte[] result = _peekedEntry;
            _peekedEntry = null;
            return result;
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
        if (_peekedEntry != null) {
            byte[] result = _peekedEntry;
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
    public synchronized int readNextEntry(byte[] buffer, int offset) throws InterruptedException
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
    public synchronized int readNextEntryIfAvailable(byte[] buffer, int offset)
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
    public synchronized int readNextEntry(long timeoutMsecs, byte[] buffer, int offset)
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
    public synchronized byte[] peekNextEntry()
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

        // otherwise we got negated version of partial length, so find what we got:
        len = -len - 1;

        // and move to read the next segment;
        String error = _freeReadSegment(null);
        if (error != null) {
            throw new IllegalStateException(error);
        }
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

    private int _doReadNext(byte[] buffer, int offset)
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

    private int _doReadPeekedEntry(byte[] buffer, int offset)
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
}
