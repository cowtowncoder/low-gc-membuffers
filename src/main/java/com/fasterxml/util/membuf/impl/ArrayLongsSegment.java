package com.fasterxml.util.membuf.impl;

import com.fasterxml.util.membuf.SegmentAllocator;
import com.fasterxml.util.membuf.base.*;

/**
 * {@link LongsSegment} implementation that uses POJAs (Plain Old Java Array)
 * for storing long sequences.
 */
public class ArrayLongsSegment extends LongsSegment
{
    protected final long[] _buffer;

    protected int _appendPtr;
    
    protected int _readPtr;
    
    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */
    
    public ArrayLongsSegment(int size)
    {
        _buffer = new long[size];
    }

    /**
     * Factory method for construction {@link LongsSegmentAllocator} that
     * constructs instances of this segment type
     */
    public static LongsSegmentAllocator allocator(int segmentSize, int minSegmentsToRetain, int maxSegments) {
        return new Allocator(segmentSize, minSegmentsToRetain, maxSegments);
    }
    
    /*
    /**********************************************************************
    /* API: state changes
    /**********************************************************************
     */

    @Override
    public ArrayLongsSegment initForWriting()
    {
        super.initForWriting();
        _appendPtr = 0;
        return this;
    }

    //public Segment finishWriting()
    
    @Override
    public ArrayLongsSegment initForReading()
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
    public final int availableForAppend() {
        return _buffer.length - _appendPtr;
    }

    @Override
    public final int availableForReading() {
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
    public void append(long[] src, int offset, int length) {
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
    public int tryAppend(long[] src, int offset, int length)
    {
        int actualLen = Math.min(length, availableForAppend());
        if (actualLen > 0) {
            int dst = _appendPtr;
            _appendPtr += length;
            System.arraycopy(src, offset, _buffer, dst, actualLen);
        }
        return actualLen;
    }

    @Override
    public boolean tryAppend(long value)
    {
        if (availableForAppend() < 1) {
            return false;
        }
        _buffer[_appendPtr++] = value;
        return true;
    }

    /*
    /**********************************************************************
    /* Reading data
    /**********************************************************************
     */

    @Override
    public int readLength()
    {
        if (availableForReading() > 1) {
            return -1;
        }
        return (int) _buffer[_readPtr++];
    }
    
    @Override
    public void read(long[] buffer, int offset, int length)
    {
        int src = _readPtr;
        _readPtr += length;
        System.arraycopy(_buffer, src, buffer, offset, length);
    }

    /**
     * @return Number of bytes actually read
     */
    @Override
    public int tryRead(long[] buffer, int offset, int length)
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

    /*
    /**********************************************************************
    /* Helper class: Allocator for this segment type
    /**********************************************************************
     */
    
    /**
     * {@link SegmentAllocator} implementation that allocates
     * {@link ArrayLongsSegment}s.
     */
    public static class Allocator extends LongsSegmentAllocator
    {
        public Allocator(int segmentSize, int minSegmentsToRetain, int maxSegments) {
            super(segmentSize, minSegmentsToRetain, maxSegments);
        }
        
        protected LongsSegment _allocateSegment()
        {
            // can reuse a segment returned earlier?
            if (_reusableSegmentCount > 0) {
                LongsSegment segment = _firstReusableSegment;
                _firstReusableSegment = segment.getNext();
                ++_bufferOwnedSegmentCount; 
                --_reusableSegmentCount;
                return segment;
            }
            LongsSegment segment = new ArrayLongsSegment(_segmentSize);
            ++_bufferOwnedSegmentCount; 
            return segment;
        }
    }
}
