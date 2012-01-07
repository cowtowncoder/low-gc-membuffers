package com.fasterxml.util.membuf.impl;

import com.fasterxml.util.membuf.base.BytesSegment;

public class ArrayBytesSegment extends BytesSegment
{
    protected final byte[] _buffer;

    protected int _appendPtr;
    
    protected int _readPtr;
    
    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */
    
    public ArrayBytesSegment(int size)
    {
        _buffer = new byte[size];
    }

    /*
    /**********************************************************************
    /* API: state changes
    /**********************************************************************
     */

    @Override
    public ArrayBytesSegment initForWriting()
    {
        super.initForWriting();
        _appendPtr = 0;
        return this;
    }

    //public Segment finishWriting()
    
    @Override
    public ArrayBytesSegment initForReading()
    {
        super.initForReading();
        _readPtr = 0;
        return this;
    }

    //public Segment finishReading()

    @Override
    public void clear()
    {
        super.clear();
        _readPtr = _appendPtr = 0;
    }

    /*
    /**********************************************************************
    /* Package methods, properties
    /**********************************************************************
     */

    @Override
    public int availableForAppend() {
        return _buffer.length - _appendPtr;
    }

    @Override
    public int availableForReading() {
        return _buffer.length - _readPtr;
    }

    /*
    /**********************************************************************
    /* Package methods, appending data
    /**********************************************************************
     */
    
    /**
     * Append operation that appends specified data; caller must ensure
     * that it will actually fit (if it can't, it should instead call
     * {@link #tryAppend}).
     */
    @Override
    public void append(byte[] src, int offset, int length) {
        int dst = _appendPtr;
        _appendPtr += length;
        System.arraycopy(src, offset, _buffer, dst, length);
    }

    /**
     * Append operation that tries to append as much of input data as
     * possible, and returns number of bytes that were copied
     * 
     * @return Number of bytes actually appended
     */
    @Override
    public int tryAppend(byte[] src, int offset, int length)
    {
        int actualLen = Math.min(length, availableForAppend());
        if (actualLen > 0) {
            int dst = _appendPtr;
            _appendPtr += length;
            System.arraycopy(src, offset, _buffer, dst, actualLen);
        }
        return actualLen;
    }

    /*
    /**********************************************************************
    /* Reading data
    /**********************************************************************
     */

    @Override
    public void read(byte[] buffer, int offset, int length)
    {
        int src = _readPtr;
        _readPtr += length;
        System.arraycopy(_buffer, src, buffer, offset, length);
    }

    /**
     * @return Number of bytes actually read
     */
    @Override
    public int tryRead(byte[] buffer, int offset, int length)
    {
        length = Math.min(availableForReading(), length);
        if (length > 0) {
            int src = _readPtr;
            _readPtr += length;
            System.arraycopy(_buffer, src, buffer, offset, length);
        }
        return length;
    }
    
    @Override
    public int skip(int length)
    {
        length = Math.min(length, availableForReading());
        _readPtr += length;
        return length;
    }

    /**
     * Method for reading as much of the length prefix as possible from
     * the current pointer in this segment. This will be from 0 to 5 bytes,
     * depending on length of prefix, and whether it resides completely in
     * this segment or not.
     */
    @Override
    public int readLength()
    {
        int available = availableForReading();
        if (available == 0) {
            return -1;
        }
        int length = _buffer[_readPtr++];

        if (length < 0) { // single-byte length, simple
            return (length & 0x7F);
        }
        if (--available == 0) {
            return -(length + 1);
        }

        // second byte:
        int b = _buffer[_readPtr++];
        if (b < 0) { // two-byte completed
            return (length << 7) + (b & 0x7F);
        }
        length = (length << 7) + b;
        if (--available == 0) {
            return -(length + 1);
        }

        // third byte:
        b = _buffer[_readPtr++];
        if (b < 0) {
            return (length << 7) + (b & 0x7F);
        }
        length = (length << 7) + b;
        if (--available == 0) {
            return -(length + 1);
        }

        // fourth byte:
        b = _buffer[_readPtr++];
        if (b < 0) {
            return (length << 7) + (b & 0x7F);
        }
        length = (length << 7) + b;
        if (--available == 0) {
            return -(length + 1);
        }

        // fifth and last byte
        b = _buffer[_readPtr++];
        if (b < 0) {
            return (length << 7) + (b & 0x7F);
        }
        // ... or, corrupt data
        throw new IllegalStateException("Corrupt segment: fifth byte of length was 0x"+Integer.toHexString(b)+" did not have high-bit set");
    }

    /**
     * Method that is called to read length prefix that is split across
     * segment boundary. Due to
     */
    @Override
    public int readSplitLength(int partial)
    {
        while (true) {
            partial = (partial << 7);
            int b = _buffer[_readPtr++];
            if (b < 0) { // complete...
                return partial + (b & 0x7F);
            }
            partial += b;
        }
    }
}
