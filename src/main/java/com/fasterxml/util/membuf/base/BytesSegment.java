package com.fasterxml.util.membuf.base;

public abstract class BytesSegment extends SegmentBase<BytesSegment>
{
    /*
    /**********************************************************************
    /* Byte-specific API: reading data
    /**********************************************************************
     */
    
    public abstract int readLength();

    public abstract int readSplitLength(int partial);

    public abstract void read(byte[] buffer, int offset, int length);

    public abstract int tryRead(byte[] buffer, int offset, int length);

    public abstract int skip(int length);

}
