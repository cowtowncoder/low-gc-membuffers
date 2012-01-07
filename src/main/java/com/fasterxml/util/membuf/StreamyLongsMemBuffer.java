package com.fasterxml.util.membuf;

import com.fasterxml.util.membuf.base.LongsSegment;
import com.fasterxml.util.membuf.base.StreamyMemBufferBase;

public abstract class StreamyLongsMemBuffer extends StreamyMemBufferBase<LongsSegment>
{
    protected StreamyLongsMemBuffer(SegmentAllocator<LongsSegment> allocator,
            int minSegmentsToAllocate, int maxSegmentsToAllocate,
            LongsSegment initialSegments) {
        super(allocator, minSegmentsToAllocate, maxSegmentsToAllocate, initialSegments);
    }

    /*
    /**********************************************************************
    /* Public API, simple statistics (not data) accessors
    /**********************************************************************
     */

    @Override
    public synchronized boolean isEmpty() {
        // !!! TODO
        return true;
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
    public abstract void append(long value);
    
    /**
     * Method that tries to append data in buffer and returning;
     * if there is no room, a {@link IllegalStateException} is thrown.
     */
    public abstract void append(long[] data);

    /**
     * Method that tries to append data in buffer and returning;
     * if there is no room, a {@link IllegalStateException} is thrown.
     */
    public abstract void append(byte[] data, int dataOffset, int dataLength);

    /**
     * Method that tries to append data in buffer if there is enough room;
     * if there is, data appended and 'true' returned; otherwise no changes
     * are made and 'false' is returned.
     */
    public abstract boolean tryAppend(byte[] data);
    
    /**
     * Method that tries to append data in buffer if there is enough room;
     * if there is, data appended and 'true' returned; otherwise no changes
     * are made and 'false' is returned.
     */
    public abstract boolean tryAppend(byte[] data, int dataOffset, int dataLength);

    /*
    /**********************************************************************
    /* Public API, reading next entry in caller-provided array
    /**********************************************************************
     */

    public abstract int read() throws InterruptedException;
    
    /**
     * Method for reading and removing next available entry from buffer and
     * return length of the entry in bytes, if succesful; or, if buffer does
     * not have enough space, return negative number as error code.
     * If no entry is available, will block to wait for more data.
     * 
     * @param buffer Buffer in which entry is to be read: must have enough space
     *  for read to succeed
     * @param offset Offset in buffer to use for storing results
     *
     * @return Length of the entry (non-negative) if read succeeds;
     *   or, negative number that indicates length of the entry in case
     *   of failures: for example, if buffer only had space for 4 bytes,
     *   and entry length was 6, would return -6.
     */
    public abstract int read(byte[] buffer, int offset, int length) throws InterruptedException;

    /**
     * Method for reading and removing next available entry from buffer and
     * return length of the entry in bytes, if successful; or, if buffer does
     * not have enough space, return negative number as error code.
     * If no entry is available, will return {@link Integer.MIN_VALUE}.
     * 
     * @param buffer Buffer in which entry is to be read: must have enough space
     *  for read to succeed
     * @param offset Offset in buffer to use for storing results
     *
     * @return {@link Integer#MIN_VALUE} if no entry was available,
     *   length of the entry (non-negative) read if read succeeds,
     *   or negative number that indicates length of the entry in case
     *   of failures: for example, if buffer only had space for 4 bytes,
     *   and entry length was 6, would return -6.
     */
    public abstract int readIfAvailable(byte[] buffer, int offset, int length);
    
    /**
     * Method for reading and removing next entry from the buffer, if one
     * is available.
     * If buffer is empty, may wait up to specified amount of time for new data to arrive.
     * If no entry is available after timeout, will return {@link Integer.MIN_VALUE}.
     * If length of entry exceeds available buffer space, will return negative number
     * that indicates length of the entry that would have been copied.
     * 
     * @param timeoutMsecs Amount of time to wait for more data if
     *   buffer is empty, if non-zero positive number; if zero or
     *   negative number, will return immediately
     * @param buffer Buffer in which entry is to be read: must have enough space
     *  for read to succeed
     * @param offset Offset in buffer to use for storing results
     *
     * @return {@link Integer#MIN_VALUE} if no entry was available,
     *   length of the entry (non-negative) read if read succeeds,
     *   or negative number that indicates length of the entry in case
     *   of failures: for example, if buffer only had space for 4 bytes,
     *   and entry length was 6, would return -6.
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

    /*
    @Override
    public synchronized void waitForNextEntry() throws InterruptedException
    {
        if (_head == null) {
            _reportClosed();
        }
        if (_entryCount == 0 && _peekedEntry == null) {
            this.wait();
        }
    }

    @Override
    public synchronized void waitForNextEntry(long maxWaitMsecs) throws InterruptedException
    {
        if (_head == null) {
            _reportClosed();
        }
        if (_entryCount == 0 && _peekedEntry == null) {
            this.wait(maxWaitMsecs);
        }
    }    
    */

    /*
    /**********************************************************************
    /* Abstract method implementations
    /**********************************************************************
     */
}
