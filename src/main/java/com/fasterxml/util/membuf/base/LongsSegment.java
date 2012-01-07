package com.fasterxml.util.membuf.base;

/**
 * Intermediate base class for {@link Segment}s used to store
 * long-valued sequences.
 */
public abstract class LongsSegment extends SegmentBase<LongsSegment>
{
    /*
    /**********************************************************************
    /* long-specific API: reading data
    /**********************************************************************
     */

    /**
     * Method for reading length indicator.
     */
    public abstract int readLength();

    public abstract void read(long[] buffer, int offset, int length);

    public abstract int tryRead(long[] buffer, int offset, int length);

    /**
     * Method for trying to skip up to specified number of bytes.
     */
    public abstract int skip(int length);

    /*
    /**********************************************************************
    /* long-specific API: appending data
    /**********************************************************************
     */
    
    /**
     * Append operation that appends specified data; caller must ensure
     * that it will actually fit (if it can't, it should instead call
     * {@link #tryAppend}).
     */
    public abstract void append(long[] src, int offset, int length);

    /**
     * Append operation that tries to append as much of input data as
     * possible, and returns number of bytes that were copied
     * 
     * @return Number of bytes actually appended
     */
    public abstract int tryAppend(long[] src, int offset, int length);

    /**
     * Append operation that tries to append given long value in this
     * segment.
     * 
     * @return True if there was room and append succeeded; false if segment
     *    is full
     */
    public abstract boolean tryAppend(long value);
}
