package com.fasterxml.util.membuf.impl;

import com.fasterxml.util.membuf.*;
import com.fasterxml.util.membuf.base.BytesSegment;

public class StreamyBytesMemBufferImpl extends StreamyBytesMemBuffer
{
    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    /**
     * @param allocator Allocator used for allocating underlying segments
     * @param minSegmentsToAllocate Maximum number of segments to hold on to
     *   (for reuse) after being released. Increases minimum memory usage
     *   but can improve performance by avoiding unnecessary re-allocation;
     *   and also guarantees that buffer always has at least this much
     *   storage capacity.
     * @param maxSegmentsToAllocate Maximum number of segments that can be
     *   allocated for this buffer: limits maximum capacity and memory usage
     * @param initialSegments Chain of pre-allocated segments, containing
     *   <code>_maxSegmentsForReuse</code> segments that are allocated to ensure
     *   that there is always specified minimum capacity available
     */
    public StreamyBytesMemBufferImpl(SegmentAllocator<BytesSegment> allocator,
            int minSegmentsToAllocate, int maxSegmentsToAllocate,
            BytesSegment initialSegments) {
        super(allocator, minSegmentsToAllocate, maxSegmentsToAllocate, initialSegments);
    }

    /*
    /**********************************************************************
    /* Public API, simple statistics (not data) accessors
    /**********************************************************************
     */

    @Override
    public synchronized boolean isEmpty() {
        return _totalPayloadLength > 0L;
    }

    @Override
    public synchronized long available() {
        return _totalPayloadLength;
    }
    
    /*
    /**********************************************************************
    /* Public API, appending
    /**********************************************************************
     */

    @Override
    public synchronized void append(byte value) {
        // TODO Auto-generated method stub
    }
    
    @Override
    public synchronized void append(byte[] data) {
        // TODO Auto-generated method stub
    }

    @Override
    public synchronized void append(byte[] data, int dataOffset, int dataLength) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public synchronized boolean tryAppend(byte value) {
        return false;
    }
    
    @Override
    public synchronized boolean tryAppend(byte[] data) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public synchronized boolean tryAppend(byte[] data, int dataOffset, int dataLength) {
        // TODO Auto-generated method stub
        return false;
    }

    /*
    /**********************************************************************
    /* Public API, reading
    /**********************************************************************
     */
    
    @Override
    public synchronized int read() throws InterruptedException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public synchronized int read(byte[] buffer, int offset, int length)
            throws InterruptedException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public synchronized int readIfAvailable(byte[] buffer, int offset, int length) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public synchronized int read(long timeoutMsecs, byte[] buffer, int offset, int length)
            throws InterruptedException {
        // TODO Auto-generated method stub
        return 0;
    }

    /*
    /**********************************************************************
    /* Abstract method impls
    /**********************************************************************
     */

    // // // No peeked data, so these are simple
    
    @Override
    protected void _clearPeeked() { }

    @Override
    protected int _peekedLength() {
        return 0;
    }    
}
