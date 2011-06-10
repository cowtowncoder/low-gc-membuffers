package com.fasterxml.membuf;

public class SimpleAppendRead extends MembufTestBase
{
    public void testX() throws Exception
    {
        // will use segments of size 10 bytes; only one segment per-allocator reuse
        // and maximum allocation of 3 segments per-allocator
        MemBuffers bufs = new MemBuffers(10, 1, 3);
        // buffer will have similar limits
        MemBuffer buffer = bufs.createBuffer(1, 2);

        assertEquals(0, buffer.getEntryCount());
        assertEquals(10, buffer.getMemoryUsed());
        assertEquals(-1, buffer.getNextEntryLength());

        assertNull(buffer.getNextEntry());
        
        byte[] chunk1 = buildChunk(3);
        buffer.appendEntry(chunk1);
        assertEquals(1, buffer.getEntryCount());
        // should take 4 bytes (length, payload); but let's remove first right away

        byte[] actual = buffer.getNextEntry();
        assertNotNull(actual);
        verifyChunk(actual, chunk1.length);
    }

    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */
}
