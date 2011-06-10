package com.fasterxml.membuf;

public class SimpleAppendReadTest extends MembufTestBase
{
    public void testX() throws Exception
    {
        // will use segments of size 10 bytes; only one segment per-allocator reuse
        // and maximum allocation of 4 segments per-allocator
        MemBuffers bufs = new MemBuffers(10, 1, 4);
        // buffer will have similar limits
        MemBuffer buffer = bufs.createBuffer(1, 3);

        assertEquals(0, buffer.getEntryCount());
        assertEquals(0, buffer.getTotalPayloadLength());
        // no content, beginning of buffer, all 30 bytes available...
        assertEquals(30, buffer.getMaximumAvailableSpace());
        assertEquals(-1, buffer.getNextEntryLength());

        assertNull(buffer.getNextEntry());
        
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
        actual = buffer.getNextEntry();
        verifyChunk(actual, chunk7.length);
        actual = buffer.getNextEntry();
        verifyChunk(actual, chunk7.length);
        actual = buffer.getNextEntry();
        verifyChunk(actual, chunk7.length);

        // and now we should be empty...
        assertEquals(0, buffer.getEntryCount());
        assertEquals(0, buffer.getTotalPayloadLength());
        assertEquals(1, buffer.getSegmentCount());
    }

    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */
}
