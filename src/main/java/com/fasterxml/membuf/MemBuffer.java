package com.fasterxml.membuf;

/*
 * Copyright Tatu Saloranta, 2011-
 */

/**
 * Actual memory queue implementation, which uses set of {@link Segment}s as
 * virtual ring buffer. Number of segments used is bound by minimum and maximum
 * amounts, which defines minimim and maximum memory usage.
 * Memory usage is relatively easy to estimate since data is stored as
 * byte sequences and almost all memory is simply used by
 * allocated <code>ByteBuffer</code>s.
 *<p>
 * Access to queue is fully synchronized, since parts will have to be anyway
 * (updating of stats, pointers), and since all real-world use cases will
 * need some level of synchronization anyway, even with just single producer
 * and consumer. If it turns out that there are bottlenecks that could be
 * avoided with more granular (or external) locking, this design can be
 * revisited.
 * 
 * @author Tatu Saloranta
 */
public class MemBuffer
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
     * less than equal to {@link _minSegments}.
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
     * @param maxSegmentsToRetain Maximum number of segments to hold on to
     *   (for reuse) after being released. Increases minimum memory usage
     *   but can improve performance by avoiding unnecessary re-allocation;
     *   and also guarantees that buffer always has at least this much
     *   storage capacity.
     * @param maxSegmentsToAllocate Maximum number of segments that can be
     *   allocated for this buffer: limits maximum capacity and memory usage
     * @param initialSegments Chain of pre-allocated segments, containing
     *   <code>maxSegmentsToRetain</code> segments that are allocated to ensure
     *   that there is always specified minimum capacity available
     */
    public MemBuffer(SegmentAllocator allocator,
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
        _head.initForWriting(null);
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

    /**
     * Method for checking how many entries are buffered in this buffer
     * currently.
     */
    public synchronized int getEntryCount() {
        return _entryCount;
    }

    public synchronized boolean isEmpty() {
        return _entryCount > 0;
    }
    
    /**
     * Method for checking how many segments are currently used for
     * storing data (not including segments that may be retained
     * for reuse but do not contain data)
     */
    public synchronized int getSegmentCount() {
        return _usedSegmentsCount;
    }

    /**
     * Method for checking what would be the maximum available space
     * available for appending more entries. Since there is per-entry
     * overhead (length prefix of at least one and at most five bytes),
     * this is not all available for entries, but gives an approximate
     * idea of that amount.
     */
    public synchronized long getMaximumAvailableSpace()
    {
        // First: how much room do we have in the current write segment?
        long space = _head.availableForAppend();
        // and how many more segments could we allocate?
        int canAllocate = (_maxSegmentsToAllocate - _usedSegmentsCount);

        if (canAllocate > 0) {
            space += (long) canAllocate * (long) _segmentSize;
        }
        return space;
    }

    /**
     * Method for checking total amount of payload buffered in this buffer.
     * This does not include entry metadata (length prefixes).
     */
    public synchronized long getTotalPayloadLength()
    {
        return _totalPayloadLength;
    }
    
    /*
    /**********************************************************************
    /* Public API, write (append)
    /**********************************************************************
     */

    public final void appendEntry(byte[] data) {
        appendEntry(data, 0, data.length);
    }

    public void appendEntry(byte[] data, int dataOffset, int dataLength)
    {
        if (!tryAppendEntry(data, dataOffset, dataLength)) {
            throw new IllegalStateException("Not enough room in buffer to append entry of "+dataLength
                    +" (can't allocate enough new segments)");
        }
    }

    public final boolean tryAppendEntry(byte[] data) {
        return tryAppendEntry(data, 0, data.length);
    }
    
    public synchronized boolean tryAppendEntry(byte[] data, int dataOffset, int dataLength)
    {
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

    /**
     * Helper method that handles append when contents may need to be split
     * across multiple segments.
     */
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
            _head = seg = _reuseFree().initForWriting(seg);
        }
    }

    /**
     * Helper method for reusing a segment from free-segments list.
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
    
    /*
    /**********************************************************************
    /* Public API, reading
    /**********************************************************************
     */

    /**
     * Method that will check size of the next entry, if buffer has entries;
     * returns size in bytes if there is at least one entry, or -1 if buffer
     * is empty.
     */
    public synchronized int getNextEntryLength()
    {
        int len = _nextEntryLength;
        if (len < 0) { // need to read it?
            if (_entryCount == 0) { // but can only read if something is actually available
                return -1;
            }
            _nextEntryLength = len = _readEntryLength();
        }
        return len;
    }
    
    /**
     * Method for reading and removing next available entry from buffer.
     * If no ent
     */
    public synchronized byte[] getNextEntry()
    {
        // first: must have something to return
        if (_entryCount == 0) {
            return null;
        }
        int segLen = getNextEntryLength();
        // but ensure that it gets reset for chunk after this one
        _nextEntryLength = -1;
        // and reduce entry count as well
        --_entryCount;
        _totalPayloadLength -= segLen;

        // a trivial case; marker entry (no payload)
        if (segLen == 0) {
            return EMPTY_PAYLOAD;
        }
        
        byte[] result = new byte[segLen];

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
        Segment old = _tail;
        Segment next = old.getNext();
        old.finishReading();
        --_usedSegmentsCount;
        _tail = next.initForReading();
        // how about freed segment? reuse?
        if ((_usedSegmentsCount + _freeSegmentCount) < _maxSegmentsForReuse) {
            _firstFreeSegment = _firstFreeSegment.relink(old);
            ++_freeSegmentCount;
        }
        // and then read enough data to figure out length:
        return _tail.readSplitLength(len);
    }

    /**
     * Helper method that handles append when contents may need to be split
     * across multiple segments.
     */
    protected void _doReadChunked(byte[] buffer, int offset, int length)
    {
        Segment seg = _tail;
        while (true) {
            int actual = seg.tryRead(buffer, offset, length);
            offset += actual;
            length -= actual;
            if (length == 0) { // complete, can leave
                return;
            }
            // otherwise, need another segment, so complete current read
            seg.finishReading();
            // and allocate, init-for-writing new one:
            _tail = seg = _tail.getNext().initForReading();
        }
    }
    
    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */

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
