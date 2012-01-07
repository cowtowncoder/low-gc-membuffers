package com.fasterxml.util.membuf.impl;

import com.fasterxml.util.membuf.SegmentAllocator;
import com.fasterxml.util.membuf.StreamyLongsMemBuffer;
import com.fasterxml.util.membuf.base.LongsSegment;

public class StreamyLongsMemBufferImpl extends StreamyLongsMemBuffer
{
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
    public StreamyLongsMemBufferImpl(SegmentAllocator<LongsSegment> allocator,
                int minSegmentsToAllocate, int maxSegmentsToAllocate,
                LongsSegment initialSegments) {
        super(allocator, minSegmentsToAllocate, maxSegmentsToAllocate, initialSegments);
    }

    /*
    /**********************************************************************
    /* Public API, simple statistics (not data) accessors
    /**********************************************************************
     */

    @Override
    public synchronized boolean isEmpty() {
        // !!! TODO
        return true;
    }

    /*
    /**********************************************************************
    /* Public API, appending
    /**********************************************************************
     */

    @Override
    public void append(long value) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void append(long[] data) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void append(byte[] data, int dataOffset, int dataLength) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public boolean tryAppend(byte[] data) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean tryAppend(byte[] data, int dataOffset, int dataLength) {
        // TODO Auto-generated method stub
        return false;
    }

    /*
    /**********************************************************************
    /* Public API, reading
    /**********************************************************************
     */
    
    @Override
    public int read() throws InterruptedException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int read(byte[] buffer, int offset, int length)
            throws InterruptedException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int readIfAvailable(byte[] buffer, int offset, int length) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int read(long timeoutMsecs, byte[] buffer, int offset, int length)
            throws InterruptedException {
        // TODO Auto-generated method stub
        return 0;
    }

    /*
    /**********************************************************************
    /* Public API, waiting
    /**********************************************************************
     */

    @Override
    public void waitForNextEntry() throws InterruptedException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void waitForNextEntry(long maxWaitMsecs) throws InterruptedException {
        // TODO Auto-generated method stub
        
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
