package com.fasterxml.util.membuf;

import org.junit.Assert;

public class SimpleAppendReadTest extends MembufTestBase
{
    public void testSimpleAppendAndGet() throws Exception
    {
        _testSimpleAppendAndGet(Allocator.BYTE_BUFFER_DIRECT);
        _testSimpleAppendAndGet(Allocator.BYTE_BUFFER_FAKE);
        _testSimpleAppendAndGet(Allocator.BYTE_ARRAY);
    }

    public void testSimpleAppendAndRead() throws Exception
    {
        _testSimpleAppendAndRead(Allocator.BYTE_BUFFER_DIRECT);
        _testSimpleAppendAndRead(Allocator.BYTE_BUFFER_FAKE);
        _testSimpleAppendAndRead(Allocator.BYTE_ARRAY);
    }    

    public void testEmptySegments() throws Exception
    {
        _testEmptySegments(Allocator.BYTE_BUFFER_DIRECT);
        _testEmptySegments(Allocator.BYTE_BUFFER_FAKE);
        _testEmptySegments(Allocator.BYTE_ARRAY);
    }

    public void testTryReadFromEmpty() throws Exception
    {
        _testTryReadFromEmpty(Allocator.BYTE_BUFFER_DIRECT);
        _testTryReadFromEmpty(Allocator.BYTE_BUFFER_FAKE);
        _testTryReadFromEmpty(Allocator.BYTE_ARRAY);
    }    
    
    /*
    /**********************************************************************
    /* Actual test impls
    /**********************************************************************
     */

    private void _testSimpleAppendAndGet(Allocator aType) throws Exception
    {
        // will use segments of size 10 bytes; only one segment per-allocator reuse
        // and maximum allocation of 4 segments per-allocator
        final MemBuffersForBytes bufs = createBytesBuffers(aType, 10, 1, 4);
        // buffer will have similar limits
        final ChunkyBytesMemBuffer buffer = bufs.createChunkyBuffer(1, 3);

        assertEquals(0, buffer.getEntryCount());
        assertEquals(0, buffer.getTotalPayloadLength());
        assertTrue(buffer.isEmpty());
        // no content, beginning of buffer, all 30 bytes available...
        assertEquals(30, buffer.getMaximumAvailableSpace());
        assertEquals(-1, buffer.getNextEntryLength());

        assertNull(buffer.getNextEntryIfAvailable());
        
        byte[] chunk3 = buildBytesChunk(3);
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
        byte[] chunk7 = buildBytesChunk(7);
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

    // Test 'read' methods (where called hands buffer to use)
    private void _testSimpleAppendAndRead(Allocator aType) throws Exception
    {
        final MemBuffersForBytes bufs = createBytesBuffers(aType, 10, 1, 4);
        final ChunkyBytesMemBuffer buffer = bufs.createChunkyBuffer(1, 3);

        assertEquals(0, buffer.getEntryCount());
        assertEquals(0, buffer.getTotalPayloadLength());
        assertTrue(buffer.isEmpty());
        // first, reads from empty buffer should fail as usual
        assertEquals(Integer.MIN_VALUE, buffer.readNextEntryIfAvailable(new byte[1], 0));

        // then append a 2-byte segment
        byte[] data = { 1, 2 };
        buffer.appendEntry(data);
        assertEquals(1, buffer.getEntryCount());
        assertEquals(2, buffer.getTotalPayloadLength());
        assertFalse(buffer.isEmpty());

        // and try read; first with unsufficient buffer
        assertEquals(-2, buffer.readNextEntryIfAvailable(new byte[1], 0));
        byte[] result = new byte[2];
        assertEquals(-2, buffer.readNextEntryIfAvailable(result, 1));

        // but succeed with enough space
        assertEquals(2, buffer.readNextEntryIfAvailable(result, 0));
        assertEquals((byte) 1, result[0]);
        assertEquals((byte) 2, result[1]);
        assertEquals(0, buffer.getEntryCount());
        assertEquals(0, buffer.getTotalPayloadLength());
        assertTrue(buffer.isEmpty());

        // then verify that split read works too
        data = new byte[25];
        for (int i = 0; i < data.length; ++i) {
            data[i] = (byte) i;
        }
        buffer.appendEntry(data);
        
        result = new byte[25];
        assertEquals(25, buffer.readNextEntry(10L, result, 0));
        Assert.assertArrayEquals(data, result);
    }
    
    /**
     * Separate test for appending and reading empty segments; segments
     * with 0 bytes of payload which consist of just a single length
     * indicator byte.
     */
    private void _testEmptySegments(Allocator aType) throws Exception
    {
        final MemBuffersForBytes bufs = createBytesBuffers(aType, 10, 1, 3);
        final ChunkyBytesMemBuffer buffer = bufs.createChunkyBuffer(1, 2);
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
    private void _testTryReadFromEmpty(Allocator aType) throws Exception
    {
        final ChunkyBytesMemBuffer buffer = createBytesBuffers(aType, 1000, 1, 100).createChunkyBuffer(1, 2);
        
        byte[] data = buffer.getNextEntryIfAvailable();
        assertNull(data);
        data = buffer.getNextEntry(10L); // 10 msecs delay
        assertNull(data);
    }
}
