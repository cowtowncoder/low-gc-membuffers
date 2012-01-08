package com.fasterxml.util.membuf;

import com.fasterxml.util.membuf.base.BytesSegment;
import com.fasterxml.util.membuf.base.StreamyMemBufferBase;

/**
 * Byte-valued {@link StreamyMemBuffer}: memory buffer that stores sequence
 * of bytes without preserving boundaries between different appends
 * (that is, contents of a single append can be retrieved using multiple
 * reads, as well as contents of multiple appends can be retrieved with
 * a single read)
 */
public abstract class StreamyBytesMemBuffer extends StreamyMemBufferBase<BytesSegment>
{
    protected StreamyBytesMemBuffer(SegmentAllocator<BytesSegment> allocator,
            int minSegmentsToAllocate, int maxSegmentsToAllocate,
            BytesSegment initialSegments) {
        super(allocator, minSegmentsToAllocate, maxSegmentsToAllocate, initialSegments);
    }

    /*
    /**********************************************************************
    /* Public API, write (append)
    /**********************************************************************
     */

    /**
     * Method that tries to append value in buffer and returning;
     * if there is no room, a {@link IllegalStateException} is thrown.
     */
    public final void append(byte value) {
        if (!tryAppend(value)) {
            throw new IllegalStateException("Not enough room in buffer to append a single value (can't allocate enough new segments)");
        }
    }
    
    /**
     * Method that tries to append data in buffer and returning;
     * if there is no room, a {@link IllegalStateException} is thrown.
     */
    public final void append(byte[] data) {
        append(data, 0, data.length);
    }

    /**
     * Method that tries to append data in buffer and returning;
     * if there is no room, a {@link IllegalStateException} is thrown.
     */
    public final void append(byte[] data, int dataOffset, int dataLength) {
        if (!tryAppend(data, dataOffset, dataLength)) {
            throw new IllegalStateException("Not enough room in buffer to append entry of "+dataLength
                    +" (can't allocate enough new segments)");
        }
    }

    /**
     * Method that tries to append byte value in buffer if there is enough room;
     * if there is, data appended and 'true' returned; otherwise no changes
     * are made and 'false' is returned.
     */
    public abstract boolean tryAppend(byte value);
    
    /**
     * Method that tries to append data in buffer if there is enough room;
     * if there is, data is appended and 'true' returned; otherwise no changes
     * are made and 'false' is returned.
     */
    public final boolean tryAppend(byte[] data) {
        return tryAppend(data, 0, data.length);
    }
    
    /**
     * Method that tries to append data in buffer if there is enough room;
     * if there is, data is appended and 'true' returned; otherwise no changes
     * are made and 'false' is returned.
     */
    public abstract boolean tryAppend(byte[] data, int dataOffset, int dataLength);

    /*
    /**********************************************************************
    /* Public API, reading next entry in caller-provided array
    /**********************************************************************
     */

    /**
     * Method for reading a single byte from the buffer: will block
     * if no values are yet available.
     */
    public abstract int read() throws InterruptedException;
    
    /**
     * Method for reading and removing up to specified number of values from buffer
     * and return length of data read.
     * 
     * If no data is available, will block to wait for more data.
     * 
     * @param buffer Buffer in which entry is to be read
     * @param offset Offset in buffer to use for storing results
     * @param length Maximum number of values to read
     *
     * @return Length of the read (in number of values); at least one is always
     *   read if buffer has enough room (special case being 0, in which case call
     *   may or may not block)
     */
    public abstract int read(byte[] buffer, int offset, int length) throws InterruptedException;

    /**
     * Method for reading and removing up to specified number of values from buffer
     * and return length of data read.
     * 
     * If no data is available, will immediately return 0.
     * 
     * @param buffer Buffer in which entry is to be read
     * @param offset Offset in buffer to use for storing results
     * @param length Maximum number of values to read
     *
     * @return Length of the read (in number of values) if data was available,
     *   or 0 if no data was available
     */
    public abstract int readIfAvailable(byte[] buffer, int offset, int length);
    
    /**
     * Method for reading and removing up to specified number of values from buffer
     * and return length of data read.
     * 
     * If buffer is empty, may wait up to specified amount of time for new data to arrive.
     * If no entry is available after timeout, will return 0; otherwise returns number
     * of values read
     * 
     * @param timeoutMsecs Amount of time to wait for more data if
     *   buffer is empty, if non-zero positive number; if zero or
     *   negative number, will return immediately
     * @param buffer Buffer in which entry is to be read
     * @param offset Offset in buffer to use for storing results
     * @param length Maximum number of values to read
     *
     * @return Length of the read (in number of values) if data was available,
     *   or 0 if no data was available
     */
    public abstract int read(long timeoutMsecs, byte[] buffer, int offset, int length)
        throws InterruptedException;

    /*
    /**********************************************************************
    /* Public API, read-like access: skipping, wait-for-next
    /**********************************************************************
     */
    
    @Override
    public synchronized int skip(int skipCount)
    {
        if (_head == null) {
            _reportClosed();
        }
        /*
        if (_entryCount < 1) {
            return -1;
        }
        if (_peekedEntry != null) {
            int len = _peekedEntry.length;
            _peekedEntry = null;
            return len;
        }
        
        final int segLen = getNextEntryLength();
        // ensure lengthh indicator gets reset for chunk after this one
        _nextEntryLength = -1;
        // and reduce entry count as well
        --_entryCount;
        _totalPayloadLength -= segLen;

        // a trivial case; marker entry (no payload)
        int remaining = segLen;
        String error = null;
        while (remaining > 0) {
            remaining -= _tail.skip(remaining);
            if (remaining == 0) { // all skipped?
                break;
            }
            error = _freeReadSegment(error);
        }
        if (error != null) {
            throw new IllegalStateException(error);
        }
        return segLen;
        */
        return 0;
    }
}
