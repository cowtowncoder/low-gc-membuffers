package com.fasterxml.util.membuf.base;

public abstract class BytesSegment extends SegmentBase<BytesSegment>
{
    /*
    /**********************************************************************
    /* Byte-specific API: reading data
    /**********************************************************************
     */

    /**
     * Method for trying to read VInt-encoded length indicator.
     */
    public abstract int readLength();

    /**
     * Method for reading VInt-encoded length indicator when the initial
     * read (using {@link #readLength}) failed due to value being split
     * on segment boundary.
     */
    public abstract int readSplitLength(int partial);

    public abstract void read(byte[] buffer, int offset, int length);

    public abstract int tryRead(byte[] buffer, int offset, int length);

    /**
     * Method for trying to skip up to specified number of bytes.
     */
    public abstract int skip(int length);

    /*
    /**********************************************************************
    /* Byte-specific API: appending data
    /**********************************************************************
     */
    
    /**
     * Append operation that appends specified data; caller must ensure
     * that it will actually fit (if it can't, it should instead call
     * {@link #tryAppend}).
     */
    public abstract void append(byte[] src, int offset, int length);

    /**
     * Append operation that tries to append as much of input data as
     * possible, and returns number of bytes that were copied
     * 
     * @return Number of bytes actually appended
     */
    public abstract int tryAppend(byte[] src, int offset, int length);
}
