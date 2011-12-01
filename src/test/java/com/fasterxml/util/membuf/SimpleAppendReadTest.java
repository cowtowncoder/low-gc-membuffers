package com.fasterxml.util.membuf;

import java.util.Arrays;

import com.fasterxml.util.membuf.MemBuffer;
import com.fasterxml.util.membuf.MemBuffers;

public class SimpleAppendReadTest extends MembufTestBase
{
    public void testSimpleAppendAndGet() throws Exception
    {
        // will use segments of size 10 bytes; only one segment per-allocator reuse
        // and maximum allocation of 4 segments per-allocator
        MemBuffers bufs = new MemBuffers(10, 1, 4);
        // buffer will have similar limits
        MemBuffer buffer = bufs.createBuffer(1, 3);

        assertEquals(0, buffer.getEntryCount());
        assertEquals(0, buffer.getTotalPayloadLength());
        assertTrue(buffer.isEmpty());
        // no content, beginning of buffer, all 30 bytes available...
        assertEquals(30, buffer.getMaximumAvailableSpace());
        assertEquals(-1, buffer.getNextEntryLength());

        assertNull(buffer.getNextEntryIfAvailable());
        
        byte[] chunk3 = buildChunk(3);
        buffer.appendEntry(chunk3);
        // should take 4 bytes (length, payload)
        assertEquals(1, buffer.getEntryCount());
        assertEquals(1, buffer.getSegmentCount());
        assertEquals(3, buffer.getTotalPayloadLength());
        assertEquals(26, buffer.getMaximumAvailableSpace());

        // and then let's just read it off
        byte[] actual = buffer.getNextEntry();
        assertNotNull(actual);
        verifyChunk(actual, chunk3.length);

        // but then append two 7 byte segments
        byte[] chunk7 = buildChunk(7);
        buffer.appendEntry(chunk7);
        buffer.appendEntry(chunk7);
        assertEquals(2, buffer.getEntryCount());
        assertEquals(14, buffer.getTotalPayloadLength());

        // and third one as well
        buffer.appendEntry(chunk7);
        assertEquals(3, buffer.getEntryCount());
        assertEquals(21, buffer.getTotalPayloadLength());

        // then read them all
        assertEquals(7, buffer.getNextEntryLength());
        // repeat to ensure length is not reset (or re-read)
        assertEquals(7, buffer.getNextEntryLength());
        assertEquals(7, buffer.getNextEntryLength());
        actual = buffer.getNextEntry();
        verifyChunk(actual, chunk7.length);
        actual = buffer.getNextEntry();
        verifyChunk(actual, chunk7.length);
        assertEquals(7, buffer.getNextEntryLength());
        actual = buffer.getNextEntry();
        verifyChunk(actual, chunk7.length);

        // and now we should be empty...
        assertEquals(0, buffer.getEntryCount());
        assertEquals(0, buffer.getTotalPayloadLength());
        assertTrue(buffer.isEmpty());
        // including holding on to just one segment
        assertEquals(1, buffer.getSegmentCount());

        // and shouldn't find anything else, for now
        assertNull(buffer.getNextEntryIfAvailable());
    }

    /**
     * Separate test for appending and reading empty segments; segments
     * with 0 bytes of payload which consist of just a single length
     * indicator byte.
     */
    public void testEmptySegments() throws Exception
    {
        MemBuffers bufs = new MemBuffers(10, 1, 3);
        MemBuffer buffer = bufs.createBuffer(1, 2);
        byte[] empty = new byte[0];

        assertEquals(0, buffer.getEntryCount());
        assertEquals(1, buffer.getSegmentCount());
        assertEquals(20, buffer.getMaximumAvailableSpace());

        // should be able to append 20 of empties...
        for (int i = 0; i < 20; ++i) {
            buffer.appendEntry(empty);
        }
        assertEquals(20, buffer.getEntryCount());
        assertEquals(2, buffer.getSegmentCount());

        for (int i = 0; i < 20; ++i) {
            byte[] data = buffer.getNextEntry();
            assertEquals(0, data.length);
        }
        assertEquals(0, buffer.getEntryCount());
        assertTrue(buffer.isEmpty());
        assertEquals(1, buffer.getSegmentCount());
    }

    /**
     * Unit test that verifies that read from empty buffer
     * would block; use timeout as verification
     */
    public void testTryReadFromEmpty() throws Exception
    {
        MemBuffers bufs = new MemBuffers(1000, 1, 100);
        MemBuffer buffer = bufs.createBuffer(1, 2);
        
        byte[] data = buffer.getNextEntryIfAvailable();
        assertNull(data);
        data = buffer.getNextEntry(10L); // 10 msecs delay
        assertNull(data);
    }

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
