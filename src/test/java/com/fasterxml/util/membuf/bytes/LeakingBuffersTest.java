package com.fasterxml.util.membuf.bytes;

import com.fasterxml.util.membuf.*;

// For [Issue#16]
public class LeakingBuffersTest extends MembufTestBase
{
    public void testSimpleLeak() throws Exception
    {
        MemBuffersForBytes factory = new MemBuffersForBytes(128,
2, 1024);
        for (int i = 0; i < 2; i++) {
            ChunkyBytesMemBuffer buf = null;
            buf = factory.createChunkyBuffer(2, 1024);
            buf.tryAppendEntry(new byte[1]);
            buf.close();
        }
    }
}
