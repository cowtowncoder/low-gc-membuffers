package com.fasterxml.util.membuf.impl;

import java.nio.ByteBuffer;

import com.fasterxml.util.membuf.*;
import com.fasterxml.util.membuf.base.BytesSegment;
import com.fasterxml.util.membuf.base.SegmentAllocatorBase;

/**
 * {@link Segment} implementation that uses {@link ByteBuffer}s for
 * storing data.
 * Basically a wrapper around (direct) {@link ByteBuffer},
 * adding state information and linkage to next segment in chain
 * (of used or free segments).
 * 
 * @author Tatu Saloranta
 */
public class ByteBufferBytesSegment extends BytesSegment
{
    /*
    /**********************************************************************
    /* Storage
    /**********************************************************************
     */
    
    /**
     * Underlying low-level buffer in which raw entry data is queued
     */
    protected final ByteBuffer _buffer;

    /**
     * Wrapper buffer used for reading previously written content; wrapper
     * used to allow separate pointers for reading and writing content.
     */
    protected ByteBuffer _readBuffer;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */
    
    public ByteBufferBytesSegment(int size, boolean useDirect)
    {
        super();
        if (size < ABSOLUTE_MINIMUM_LENGTH) {
            size = ABSOLUTE_MINIMUM_LENGTH;
        }
        _buffer = useDirect ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size);
    }


    /**
     * Factory method for construction {@link SegmentAllocatorBase} that
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
    public BytesSegment initForReading()
    {
        super.initForReading();
        _readBuffer = _buffer.asReadOnlyBuffer();
        _readBuffer.clear();
        return this;
    }

    @Override
    public BytesSegment finishReading()
    {
        BytesSegment result = super.finishReading();
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
    public int availableForAppend() {
        return _buffer.remaining();
    }

    @Override
    public int availableForReading() {
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
    public void append(byte[] src, int offset, int length) {
        _buffer.put(src, offset, length);
    }

    @Override
    public int tryAppend(byte[] src, int offset, int length)
    {
        int actualLen = Math.min(length, availableForAppend());
        if (actualLen > 0) {
            _buffer.put(src, offset, actualLen);
        }
        return actualLen;
    }

    @Override
    public boolean tryAppend(byte value)
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
    public byte read() { // caller must check bounds; otherwise it'll get out-of-bounds
        return _readBuffer.get();
    }
    
    @Override
    public void read(byte[] buffer, int offset, int length) {
        _readBuffer.get(buffer, offset, length);
    }

    @Override
    public int tryRead(byte[] buffer, int offset, int length)
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

    @Override
    public int readLength()
    {
        int available = availableForReading();
        if (available == 0) {
            return -1;
        }
        int length = _readBuffer.get();

        if (length < 0) { // single-byte length, simple
            return (length & 0x7F);
        }
        if (--available == 0) {
            return -(length + 1);
        }

        // second byte:
        int b = _readBuffer.get();
        if (b < 0) { // two-byte completed
            return (length << 7) + (b & 0x7F);
        }
        length = (length << 7) + b;
        if (--available == 0) {
            return -(length + 1);
        }

        // third byte:
        b = _readBuffer.get();
        if (b < 0) {
            return (length << 7) + (b & 0x7F);
        }
        length = (length << 7) + b;
        if (--available == 0) {
            return -(length + 1);
        }

        // fourth byte:
        b = _readBuffer.get();
        if (b < 0) {
            return (length << 7) + (b & 0x7F);
        }
        length = (length << 7) + b;
        if (--available == 0) {
            return -(length + 1);
        }

        // fifth and last byte
        b = _readBuffer.get();
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
            int b = _readBuffer.get();
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
     * {@link ByteBufferBytesSegment}s.
     */
    public static class Allocator extends SegmentAllocatorBase<BytesSegment>
    {
        protected final boolean _cfgAllocateNative;
        
        public Allocator(int segmentSize, int minSegmentsToRetain, int maxSegments,
                boolean allocateNativeBuffers)
               
        {
            super(segmentSize, minSegmentsToRetain, maxSegments);
            _cfgAllocateNative = allocateNativeBuffers;
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
            BytesSegment segment = new ByteBufferBytesSegment(_segmentSize, _cfgAllocateNative);
            ++_bufferOwnedSegmentCount; 
            return segment;
        }
    }
}
