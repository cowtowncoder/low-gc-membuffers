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
    }

    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */
}
