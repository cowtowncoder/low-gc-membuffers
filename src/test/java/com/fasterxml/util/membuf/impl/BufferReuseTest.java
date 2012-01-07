package com.fasterxml.util.membuf.impl;

import com.fasterxml.util.membuf.ByteMemBuffers;
import com.fasterxml.util.membuf.MemBuffer;
import com.fasterxml.util.membuf.MembufTestBase;
import com.fasterxml.util.membuf.SegmentAllocator;

public class BufferReuseTest extends MembufTestBase
{
    public void testGlobalReuseWithReads() throws Exception
    {
        ByteMemBuffers bufs = new ByteMemBuffers(20, 2, 10);
        MemBuffer buffer = bufs.createBuffer(1, 3);
        SegmentAllocator<?> alloc = bufs.getAllocator();

        assertEquals(1, alloc.getBufferOwnedSegmentCount());
        assertEquals(0, alloc.getReusableSegmentCount());
        
        buffer.appendEntry(new byte[24]); // fills 1 buffer, part of second
        buffer.appendEntry(new byte[17]); // fills 2nd buffer, starts third

        assertEquals(3, alloc.getBufferOwnedSegmentCount());
        assertEquals(0, alloc.getReusableSegmentCount());

        // by reading first one, should return one buffer back?
        byte[] data = buffer.getNextEntryIfAvailable();
        assertEquals(24, data.length);
        assertEquals(2, alloc.getBufferOwnedSegmentCount());
        assertEquals(1, alloc.getReusableSegmentCount());

        // and by second one, another
        data = buffer.getNextEntryIfAvailable();
        assertEquals(17, data.length);
        assertEquals(1, alloc.getBufferOwnedSegmentCount());
        assertEquals(2, alloc.getReusableSegmentCount());
    }

    public void testGlobalReuseWithClear() throws Exception
    {
        ByteMemBuffers bufs = new ByteMemBuffers(20, 2, 10);
        MemBuffer buffer = bufs.createBuffer(1, 3);
        SegmentAllocator<?> alloc = bufs.getAllocator();

        assertEquals(1, alloc.getBufferOwnedSegmentCount());
        assertEquals(0, alloc.getReusableSegmentCount());
        
        buffer.appendEntry(new byte[24]); // fills 1 buffer, part of second
        buffer.appendEntry(new byte[17]); // fills 2nd buffer, starts third

        assertEquals(3, alloc.getBufferOwnedSegmentCount());
        assertEquals(0, alloc.getReusableSegmentCount());
        assertEquals(3, buffer.getSegmentCount());

        buffer.clear();
        
        assertEquals(0, buffer.getEntryCount());
        assertEquals(0L, buffer.getTotalPayloadLength());
        assertEquals(1, buffer.getSegmentCount());

        assertEquals(1, alloc.getBufferOwnedSegmentCount());
        assertEquals(2, alloc.getReusableSegmentCount());
    }

}
