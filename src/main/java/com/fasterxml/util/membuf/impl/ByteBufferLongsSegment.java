package com.fasterxml.util.membuf.impl;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import com.fasterxml.util.membuf.Segment;
import com.fasterxml.util.membuf.SegmentAllocator;
import com.fasterxml.util.membuf.base.*;

/**
 * {@link Segment} implementation that uses {@link LongBuffer}s for
 * storing data.
 * Basically a wrapper around (direct) {@link LongBuffer},
 * adding state information and linkage to next segment in chain
 * (of used or free segments).
 * 
 * @author Tatu Saloranta
 */
public class ByteBufferLongsSegment extends LongsSegment
{
    /*
    /**********************************************************************
    /* Storage
    /**********************************************************************
     */
    
    /**
     * Underlying low-level buffer in which raw entry data is queued
     */
    protected final LongBuffer _buffer;

    /**
     * Wrapper buffer used for reading previously written content; wrapper
     * used to allow separate pointers for reading and writing content.
     */
    protected LongBuffer _readBuffer;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */
    
    public ByteBufferLongsSegment(int size, boolean useDirect)
    {
        super();
        if (size < ABSOLUTE_MINIMUM_LENGTH) {
            size = ABSOLUTE_MINIMUM_LENGTH;
        }
        // long buffers are direct iff wrapping a direct byte buffer, so:
        if (useDirect) {
            _buffer = ByteBuffer.allocateDirect(size).asLongBuffer();
        } else {
            _buffer = LongBuffer.allocate(size);
        }
    }

    /**
     * Factory method for construction {@link LongsSegmentAllocator} that
     * constructs instances of this segment type
     */
    public static Allocator allocator(int segmentSize, int minSegmentsToRetain, int maxSegments,
            boolean allocateNativeBuffers) {
        return new Allocator(segmentSize, minSegmentsToRetain, maxSegments, allocateNativeBuffers);
    }
    
    /*
    /**********************************************************************
    /* API: state changes
    /**********************************************************************
     */

    //public Segment initForWriting()
    //public Segment finishWriting()
    
    @Override
    public LongsSegment initForReading()
    {
        super.initForReading();
        _readBuffer = _buffer.asReadOnlyBuffer();
        _readBuffer.clear();
        return this;
    }

    @Override
    public LongsSegment finishReading()
    {
        LongsSegment result = super.finishReading();
        // clear write pointer for further reuse
        _buffer.clear();
        // and drop reference to read-wrapper:
        _readBuffer = null;
        return result;
    }

    /**
     * Method that will erase any content segment may have and reset
     * various pointers: will be called when clearing buffer, the last
     * remaining segment needs to be cleared.
     */
    @Override
    public void clear()
    {
        _readBuffer = null; // could/should we just reset it instead?
        _buffer.clear(); // clear the write pointer
        super.clear();
    }

    /*
    /**********************************************************************
    /* Package methods, properties
    /**********************************************************************
     */

    @Override
    public final int availableForAppend() {
        return _buffer.remaining();
    }

    @Override
    public final int availableForReading() {
        if (_readBuffer == null) { // sanity check...
            throw new IllegalStateException("Method should not be called when _readBuffer is null");
        }
        return _readBuffer.remaining();
    }

    /*
    /**********************************************************************
    /* Package methods, appending data
    /**********************************************************************
     */
    
    @Override
    public void append(long[] src, int offset, int length) {
        _buffer.put(src, offset, length);
    }

    @Override
    public int tryAppend(long[] src, int offset, int length)
    {
        int actualLen = Math.min(length, availableForAppend());
        if (actualLen > 0) {
            _buffer.put(src, offset, actualLen);
        }
        return actualLen;
    }

    @Override
    public boolean tryAppend(long value)
    {
        if (availableForAppend() > 0) {
            _buffer.put(value);
            return true;
        }
        return false;
    }
    
    /*
    /**********************************************************************
    /* Reading data
    /**********************************************************************
     */

    @Override
    public void read(long[] buffer, int offset, int length)
    {
        _readBuffer.get(buffer, offset, length);
    }

    /**
     * @return Number of bytes actually read
     */
    @Override
    public int tryRead(long[] buffer, int offset, int length)
    {
        int actualLen = Math.min(availableForReading(), length);
        if (actualLen > 0) {
            _readBuffer.get(buffer, offset, actualLen);
        }
        return actualLen;
    }
    
    @Override
    public int skip(int length)
    {
        length = Math.min(length, availableForReading());
        _readBuffer.position(_readBuffer.position() + length);
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
        if (availableForReading() < 1) {
            return -1;
        }
        // we only store positive int lengths, so this is safe:
        return (int) _readBuffer.get();
    }

    /*
    /**********************************************************************
    /* Helper class: Allocator for this segment type
    /**********************************************************************
     */
    
    /**
     * {@link SegmentAllocator} implementation that allocates
     * {@link ByteBufferLongsSegment}s.
     */
    public static class Allocator extends SegmentAllocatorBase<LongsSegment>
    {
        protected final boolean _cfgAllocateNative;

        public Allocator(int segmentSize, int minSegmentsToRetain, int maxSegments,
                boolean allocateNativeBuffers)
               
        {
            super(segmentSize, minSegmentsToRetain, maxSegments);
            _cfgAllocateNative = allocateNativeBuffers;
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
            LongsSegment segment = new ByteBufferLongsSegment(_segmentSize, _cfgAllocateNative);
            ++_bufferOwnedSegmentCount; 
            return segment;
        }
    }
}
