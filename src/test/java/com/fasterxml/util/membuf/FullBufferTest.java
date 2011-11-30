package com.fasterxml.util.membuf;

import java.util.Arrays;

import org.junit.Assert;

public class FullBufferTest extends MembufTestBase
{
    public void testTryWriteToFull() throws Exception
    {
        // up to 24 bytes of room (and 12 guaranteed)
        MemBuffers bufs = new MemBuffers(12, 1, 2);
        MemBuffer buffer = bufs.createBuffer(1, 2);
        byte[] data = new byte[16];
        Arrays.fill(data, (byte) 0xFF);

        buffer.appendEntry(data);
        assertEquals(1, buffer.getEntryCount());
        assertEquals(16L, buffer.getTotalPayloadLength());
        // 17 bytes used (1 byte vint length, 16 bytes of data); so 7 left:
        assertEquals(7L, buffer.getMaximumAvailableSpace());

        // so far so good; but won't have room for another one
        assertFalse(buffer.tryAppendEntry(data));
        // and if do without try, should get an exception
        try {
            buffer.appendEntry(data);
        } catch (IllegalStateException e) {
            verifyException(e, "can't allocate");
        }

        // state should be fine however:
        byte[] result = buffer.getNextEntry();
        Assert.assertArrayEquals(data, result);
        
        assertEquals(0, buffer.getEntryCount());
        assertEquals(0L, buffer.getTotalPayloadLength());
        /* won't have full 24 due to alignments; will have 7 from
         * partial buffer, 12 from the other one, for total of 19
         */
        assertEquals(19L, buffer.getMaximumAvailableSpace());

        // and 19 should be enough as we only need 17
        buffer.appendEntry(data);
        assertEquals(1, buffer.getEntryCount());
        assertEquals(16L, buffer.getTotalPayloadLength());
    }
}
