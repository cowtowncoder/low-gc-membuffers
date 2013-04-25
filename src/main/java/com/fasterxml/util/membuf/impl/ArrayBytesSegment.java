package com.fasterxml.util.membuf.impl;

import com.fasterxml.util.membuf.SegmentAllocator;
import com.fasterxml.util.membuf.base.BytesSegment;
import com.fasterxml.util.membuf.base.SegmentAllocatorBase;

/**
 * {@link BytesSegment} implementation that uses POJAs (Plain Old Java Array)
 * for storing byte sequences.
 */
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

    /**
     * Factory method for construction {@link SegmentAllocatorBase} that
     * constructs instances of this segment type
     */
    public static Allocator allocator(int segmentSize, int minSegmentsToRetain, int maxSegments) {
        return new Allocator(segmentSize, minSegmentsToRetain, maxSegments);
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
    
    @Override
    public void append(byte[] src, int offset, int length) {
        int dst = _appendPtr;
        _appendPtr += length;
        System.arraycopy(src, offset, _buffer, dst, length);
    }


    @Override
    public boolean tryAppend(byte value)
    {
        if (availableForAppend() < 1) {
            return false;
        }
        _buffer[_appendPtr++] = value;
        return true;
    }
    
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
    public byte read() { // caller must check bounds; otherwise it'll get out-of-bounds
        return _buffer[_readPtr++];
    }
    
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

    /*
    /**********************************************************************
    /* Helper class: Allocator for this segment type
    /**********************************************************************
     */
    
    /**
     * {@link SegmentAllocator} implementation that allocates
     * {@link ArrayBytesSegment}s.
     */
    public static class Allocator extends SegmentAllocatorBase<BytesSegment>
    {
        public Allocator(int segmentSize, int minSegmentsToRetain, int maxSegments) {
            super(segmentSize, minSegmentsToRetain, maxSegments);
        }

        @Override
        protected BytesSegment _allocateSegment()
        {
            // can reuse a segment returned earlier?
            if (_reusableSegmentCount > 0) {
                BytesSegment segment = _firstReusableSegment;
                _firstReusableSegment = segment.getNext();
                ++_bufferOwnedSegmentCount; 
                --_reusableSegmentCount;
                return segment;
            }
            ArrayBytesSegment segment = new ArrayBytesSegment(_segmentSize);
            ++_bufferOwnedSegmentCount; 
            return segment;
        }
    }

}
