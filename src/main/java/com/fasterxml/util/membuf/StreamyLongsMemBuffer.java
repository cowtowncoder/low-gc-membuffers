package com.fasterxml.util.membuf;

import com.fasterxml.util.membuf.base.LongsSegment;
import com.fasterxml.util.membuf.base.StreamyMemBufferBase;

/**
 * Long-valued {@link StreamyMemBuffer}: memory buffer that stores sequence
 * of longs without preserving boundaries between different appends
 * (that is, contents of a single append can be retrieved using multiple
 * reads, as well as contents of multiple appends can be retrieved with
 * a single read)
 */
public abstract class StreamyLongsMemBuffer extends StreamyMemBufferBase<LongsSegment>
{
    protected StreamyLongsMemBuffer(SegmentAllocator<LongsSegment> allocator,
            int minSegmentsToAllocate, int maxSegmentsToAllocate,
            LongsSegment initialSegments, MemBufferTracker tracker) {
        super(allocator, minSegmentsToAllocate, maxSegmentsToAllocate, initialSegments, tracker);
    }

    protected StreamyLongsMemBuffer(StreamyLongsMemBuffer src) {
        super(src);
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
    public final void append(long value) {
        if (!tryAppend(value)) {
            throw new IllegalStateException("Not enough room in buffer to append a single value (can't allocate enough new segments)");
        }
    }
    
    /**
     * Method that tries to append data in buffer and returning;
     * if there is no room, a {@link IllegalStateException} is thrown.
     */
    public final void append(long[] data) {
        append(data, 0, data.length);
    }

    /**
     * Method that tries to append data in buffer and returning;
     * if there is no room, a {@link IllegalStateException} is thrown.
     */
    public final void append(long[] data, int dataOffset, int dataLength) {
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
    public abstract boolean tryAppend(long value);
    
    /**
     * Method that tries to append data in buffer if there is enough room;
     * if there is, data appended and 'true' returned; otherwise no changes
     * are made and 'false' is returned.
     */
    public final boolean tryAppend(long[] data) {
        return tryAppend(data, 0, data.length);
    }
    
    /**
     * Method that tries to append data in buffer if there is enough room;
     * if there is, data appended and 'true' returned; otherwise no changes
     * are made and 'false' is returned.
     */
    public abstract boolean tryAppend(long[] data, int dataOffset, int dataLength);

    /*
    /**********************************************************************
    /* Public API, reading next entry in caller-provided array
    /**********************************************************************
     */

    /**
     * Method for reading a single long value from the buffer: will block
     * if no values are yet available.
     */
    public abstract long read() throws InterruptedException;
    
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
    public abstract int read(long[] buffer, int offset, int length) throws InterruptedException;

    public final int read(long[] buffer) throws InterruptedException {
        return read(buffer, 0, buffer.length);
    }
    
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
    public abstract int readIfAvailable(long[] buffer, int offset, int length);

    public final int readIfAvailable(long[] buffer) {
        return readIfAvailable(buffer, 0, buffer.length);
    }
    
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
    public abstract int read(long timeoutMsecs, long[] buffer, int offset, int length)
        throws InterruptedException;

    /*
    /**********************************************************************
    /* Public API, read-like access: skipping, wait-for-next
    /**********************************************************************
     */
    
    //public abstract int skip(int skipCount);
}
