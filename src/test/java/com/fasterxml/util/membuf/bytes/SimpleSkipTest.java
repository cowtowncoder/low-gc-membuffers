package com.fasterxml.util.membuf.bytes;

import com.fasterxml.util.membuf.ChunkyBytesMemBuffer;
import com.fasterxml.util.membuf.MembufTestBase;

public class SimpleSkipTest extends MembufTestBase
{
    public void testSimpleSkips() throws Exception
    {
        _testChunkySkips(SegType.BYTE_BUFFER_DIRECT);
        _testChunkySkips(SegType.BYTE_BUFFER_FAKE);
        _testChunkySkips(SegType.BYTE_ARRAY);
    }
    
    public void testChunkySkipAndRead() throws Exception
    {
        _testChunkySkipAndRead(SegType.BYTE_BUFFER_DIRECT);
        _testChunkySkipAndRead(SegType.BYTE_BUFFER_FAKE);
        _testChunkySkipAndRead(SegType.BYTE_ARRAY);
    }

    public void testChunkyLongerSkip() throws Exception
    {
        _testChunkyLongerSkip(SegType.BYTE_BUFFER_DIRECT);
        _testChunkyLongerSkip(SegType.BYTE_BUFFER_FAKE);
        _testChunkyLongerSkip(SegType.BYTE_ARRAY);
    }

    /*
    /**********************************************************************
    /* Actual test impls for chunky buffers
    /**********************************************************************
     */

    private void _testChunkySkips(SegType aType) throws Exception
    {
        // will use segments of size 10 bytes; only one segment per-allocator reuse
        // and maximum allocation of 4 segments per-allocator
        // buffer will have similar limits
        final ChunkyBytesMemBuffer buffer = createBytesBuffers(aType, 10, 1, 4).createChunkyBuffer(1, 3);

        // append 5 segments
        for (int i = 5; i > 0; --i) {
            buffer.appendEntry(new byte[i]);
        }
        assertEquals(5, buffer.getEntryCount());
        assertEquals(15, buffer.getTotalPayloadLength());
        assertFalse(buffer.isEmpty());

        // then skip all of it
        assertEquals(5, buffer.skipNextEntry());
        assertEquals(4, buffer.skipNextEntry());
        assertEquals(3, buffer.skipNextEntry());
        assertEquals(2, buffer.skipNextEntry());

        assertEquals(1, buffer.getNextEntryLength());
        assertEquals(1, buffer.getNextEntryLength());
        assertEquals(1, buffer.getNextEntryLength());
        assertEquals(1, buffer.skipNextEntry());
        // and when empty, nothing more:
        assertEquals(-1, buffer.skipNextEntry());
        assertTrue(buffer.isEmpty());
    }

    private void _testChunkySkipAndRead(SegType aType) throws Exception
    {
        final ChunkyBytesMemBuffer buffer = createBytesBuffers(aType, 10, 1, 4).createChunkyBuffer(1, 3);

        for (int i = 5; i > 0; --i) { // 5, 4, 3, 2, 1 segments
            buffer.appendEntry(new byte[i]);
        }
        assertEquals(5, buffer.getEntryCount());
        assertEquals(15, buffer.getTotalPayloadLength());
        assertFalse(buffer.isEmpty());

        // then skip all of it
        assertEquals(5, buffer.skipNextEntry());
        byte[] b = buffer.getNextEntry(10L);
        assertEquals(4, b.length);
        assertEquals(3, buffer.skipNextEntry());
        b = buffer.getNextEntry(10L);
        assertEquals(2, b.length);
        assertEquals(1, buffer.skipNextEntry());
        // and when empty, nothing more:
        assertEquals(-1, buffer.skipNextEntry());
        assertTrue(buffer.isEmpty());
    }

    // Test to verify that skip works across buffer boundaries
    private void _testChunkyLongerSkip(SegType aType) throws Exception
    {
        final ChunkyBytesMemBuffer buffer = createBytesBuffers(aType, 10, 1, 4).createChunkyBuffer(1, 3);
        // maximum: 29 data bytes, 1 for length
        buffer.appendEntry(new byte[29]);
        assertEquals(29, buffer.skipNextEntry());
        assertEquals(-1, buffer.skipNextEntry());
    }

    /*
    /**********************************************************************
    /* Actual test impls for streamy buffers
    /**********************************************************************
     */
}
