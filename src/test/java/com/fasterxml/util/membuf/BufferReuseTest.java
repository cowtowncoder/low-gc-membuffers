package com.fasterxml.util.membuf;

public class BufferReuseTest extends MembufTestBase
{
    public void testGlobalReuseWithReads() throws Exception
    {
        MemBuffers bufs = new MemBuffers(20, 2, 10);
        MemBuffer buffer = bufs.createBuffer(1, 3);
        SegmentAllocator alloc = bufs._segmentAllocator;

        assertEquals(1, alloc._bufferOwnedSegmentCount);
        assertEquals(0, alloc._reusableSegmentCount);
        
        buffer.appendEntry(new byte[24]); // fills 1 buffer, part of second
        buffer.appendEntry(new byte[17]); // fills 2nd buffer, starts third

        assertEquals(3, alloc._bufferOwnedSegmentCount);
        assertEquals(0, alloc._reusableSegmentCount);

        // by reading first one, should return one buffer back?
        byte[] data = buffer.getNextEntryIfAvailable();
        assertEquals(24, data.length);
        assertEquals(2, alloc._bufferOwnedSegmentCount);
        assertEquals(1, alloc._reusableSegmentCount);

        // and by second one, another
        data = buffer.getNextEntryIfAvailable();
        assertEquals(17, data.length);
        assertEquals(1, alloc._bufferOwnedSegmentCount);
        assertEquals(2, alloc._reusableSegmentCount);
    }

    public void testGlobalReuseWithClear() throws Exception
    {
        MemBuffers bufs = new MemBuffers(20, 2, 10);
        MemBuffer buffer = bufs.createBuffer(1, 3);
        SegmentAllocator alloc = bufs._segmentAllocator;

        assertEquals(1, alloc._bufferOwnedSegmentCount);
        assertEquals(0, alloc._reusableSegmentCount);
        
        buffer.appendEntry(new byte[24]); // fills 1 buffer, part of second
        buffer.appendEntry(new byte[17]); // fills 2nd buffer, starts third

        assertEquals(3, alloc._bufferOwnedSegmentCount);
        assertEquals(0, alloc._reusableSegmentCount);
        assertEquals(3, buffer._usedSegmentsCount);

        buffer.clear();
        
        assertEquals(0, buffer._entryCount);
        assertEquals(0L, buffer._totalPayloadLength);
        assertEquals(1, buffer._usedSegmentsCount);

        assertEquals(1, alloc._bufferOwnedSegmentCount);
        assertEquals(2, alloc._reusableSegmentCount);
    }

}
