package com.fasterxml.util.membuf.longs;

import com.fasterxml.util.membuf.*;

public class SkipLongsTest extends MembufTestBase
{
    public void testChunkySkips() throws Exception
    {
        _testChunkySkips(SegType.BYTE_ARRAY);
        _testChunkySkips(SegType.BYTE_BUFFER_FAKE);
        _testChunkySkips(SegType.BYTE_BUFFER_DIRECT);
    }
    
    public void testSimpleSkipAndRead() throws Exception
    {
        _testChunkySkipAndRead(SegType.BYTE_ARRAY);
        _testChunkySkipAndRead(SegType.BYTE_BUFFER_FAKE);
        _testChunkySkipAndRead(SegType.BYTE_BUFFER_DIRECT);
    }

    public void testLongerSkip() throws Exception
    {
        _testLongerChunky(SegType.BYTE_ARRAY);
        _testLongerChunky(SegType.BYTE_BUFFER_FAKE);
        _testLongerChunky(SegType.BYTE_BUFFER_DIRECT);
    }

    // "Streamy" tests
    
    public void testStreamySkips() throws Exception
    {
        _testStreamySkips(SegType.BYTE_BUFFER_DIRECT);
        _testStreamySkips(SegType.BYTE_BUFFER_FAKE);
        _testStreamySkips(SegType.BYTE_ARRAY);
    }
    
    public void testStreamySkipAndRead() throws Exception
    {
        _testStreamySkipAndRead(SegType.BYTE_BUFFER_DIRECT);
        _testStreamySkipAndRead(SegType.BYTE_BUFFER_FAKE);
        _testStreamySkipAndRead(SegType.BYTE_ARRAY);
    }

    public void testStreamyLongerSkip() throws Exception
    {
        _testStreamyLongerSkip(SegType.BYTE_BUFFER_DIRECT);
        _testStreamyLongerSkip(SegType.BYTE_BUFFER_FAKE);
        _testStreamyLongerSkip(SegType.BYTE_ARRAY);
    }
    
    
    /*
    /**********************************************************************
    /* Actual test impls
    /**********************************************************************
     */

    private void _testChunkySkips(SegType aType) throws Exception
    {
        // will use segments of size 10 bytes; only one segment per-allocator reuse
        // and maximum allocation of 4 segments per-allocator
        // buffer will have similar limits
        final ChunkyLongsMemBuffer buffer = createLongsBuffers(aType, 10, 1, 4).createChunkyBuffer(1, 3);

        // append 5 segments
        for (int i = 5; i > 0; --i) {
            buffer.appendEntry(new long[i]);
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
        final ChunkyLongsMemBuffer buffer = createLongsBuffers(aType, 10, 1, 4).createChunkyBuffer(1, 3);

        for (int i = 5; i > 0; --i) { // 5, 4, 3, 2, 1 segments
            buffer.appendEntry(new long[i]);
        }
        assertEquals(5, buffer.getEntryCount());
        assertEquals(15, buffer.getTotalPayloadLength());
        assertFalse(buffer.isEmpty());

        // then skip all of it
        assertEquals(5, buffer.skipNextEntry());
        long[] b = buffer.getNextEntry(10L);
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
    private void _testLongerChunky(SegType aType) throws Exception
    {
        final ChunkyLongsMemBuffer buffer = createLongsBuffers(aType, 10, 1, 4).createChunkyBuffer(1, 3);
        // maximum: 29 data bytes, 1 for length
        buffer.appendEntry(new long[29]);
        assertEquals(29, buffer.skipNextEntry());
        assertEquals(-1, buffer.skipNextEntry());
    }

    /*
    /**********************************************************************
    /* Actual test impls for streamy buffers
    /**********************************************************************
     */

    private void _testStreamySkips(SegType aType) throws Exception
    {
        final StreamyLongsMemBuffer buffer = createLongsBuffers(aType, 10, 1, 4).createStreamyBuffer(1, 3);

        // append longs in 5 pieces
        for (int i = 5; i >= 0; --i) {
            buffer.append(new long[i]);
        }
        assertEquals(15, buffer.getTotalPayloadLength());
        assertFalse(buffer.isEmpty());

        // then skip all of it, in different order
        int left = 15;
        for (int i = 0; i <= 5; ++i) {
            assertEquals(i, buffer.skip(i));
            left -= i;
            assertEquals(left, buffer.getTotalPayloadLength());
        }
        assertEquals(0, buffer.getTotalPayloadLength());
        assertTrue(buffer.isEmpty());

        // and when empty, nothing more:
        assertEquals(0, buffer.skip(100));
        assertTrue(buffer.isEmpty());
    }

    private void _testStreamySkipAndRead(SegType aType) throws Exception
    {
        final StreamyLongsMemBuffer buffer = createLongsBuffers(aType, 10, 1, 4).createStreamyBuffer(1, 3);

        for (int i = 5; i > 0; --i) { // 5, 4, 3, 2, 1 segments
            buffer.append(new long[i]);
        }
        assertEquals(15, buffer.getTotalPayloadLength());
        assertFalse(buffer.isEmpty());

        // then skip all of it
        assertEquals(5, buffer.skip(5));
        long[] b = new long[4];
        assertEquals(4, buffer.read(b));
        assertEquals(6, buffer.getTotalPayloadLength());
        assertEquals(6, buffer.skip(100));
        assertEquals(0, buffer.skip(100));
        assertTrue(buffer.isEmpty());
    }

    // Test to verify that skip works across buffer boundaries
    private void _testStreamyLongerSkip(SegType aType) throws Exception
    {
        final StreamyLongsMemBuffer buffer = createLongsBuffers(aType, 10, 1, 4).createStreamyBuffer(1, 3);
        buffer.append(new long[30]);
        assertEquals(30, buffer.skip(32));
        assertEquals(0, buffer.skip(1));
        assertTrue(buffer.isEmpty());
    }
}
