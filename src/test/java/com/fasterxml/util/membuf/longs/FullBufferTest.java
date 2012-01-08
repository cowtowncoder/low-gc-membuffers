package com.fasterxml.util.membuf.longs;

import java.util.Arrays;

import org.junit.Assert;

import com.fasterxml.util.membuf.*;

/**
 * Tests to verify handling of case where memory buffers are filled up.
 */
public class FullBufferTest extends MembufTestBase
{
    public void testTryWriteToFull() throws Exception
    {
        _testTryWriteToFull(SegType.BYTE_ARRAY);
        _testTryWriteToFull(SegType.BYTE_BUFFER_FAKE);
        _testTryWriteToFull(SegType.BYTE_BUFFER_DIRECT);
    }

    /**
     * Test for verifying that number of buffers that we can create is
     * also bound.
     */
    public void testMaxBuffers() throws Exception
    {
        _testMaxBuffers(SegType.BYTE_ARRAY);
        _testMaxBuffers(SegType.BYTE_BUFFER_FAKE);
        _testMaxBuffers(SegType.BYTE_BUFFER_DIRECT);
    }
    
    /*
    /**********************************************************************
    /* Actual test impls
    /**********************************************************************
     */
    
    /**
     * Test for verifying behavior when buffer is full.
     */
    public void _testTryWriteToFull(SegType aType) throws Exception
    {
        // up to 24 values (and 12 guaranteed)
        final MemBuffersForLongs bufs = createLongsBuffers(aType, 12, 1, 2);
        final ChunkyLongsMemBuffer buffer = bufs.createChunkyBuffer(1, 2);
        long[] data = new long[16];
        Arrays.fill(data, -1L);

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
            fail("Append should have failed");
        } catch (IllegalStateException e) {
            verifyException(e, "can't allocate");
        }

        // state should be fine however:
        long[] result = buffer.getNextEntry();
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

    public void _testMaxBuffers(SegType aType) throws Exception
    {
        // with max 5 segments, each buffer requiring at least two, can create two
        final MemBuffersForLongs bufs = createLongsBuffers(aType, 12, 1, 5);
        final ChunkyLongsMemBuffer buf1 = bufs.createChunkyBuffer(2, 4);
        assertNotNull(buf1);
        ChunkyLongsMemBuffer buf2 = bufs.createChunkyBuffer(2, 4);
        assertNotNull(buf2);

        // and then we should fail:
        try {
            bufs.createChunkyBuffer(2, 4);
            fail("Buffer creation should have failed");
        } catch (IllegalStateException e) {
            verifyException(e, "due to segment allocation limits");
        }
        assertNull(bufs.tryCreateChunkyBuffer(2, 4));

        // furthermore, should be able to extend one of buffers by one segment:
        buf1.appendEntry(new long[32]); // needs 33
        // but not with additional expansion
        assertFalse(buf1.tryAppendEntry(new long[4]));

        // and for second buffer can full up to 24 values:
        buf2.appendEntry(new long[23]);
        // but not for more
        try {
            buf2.appendEntry(new long[5]);
            fail("Append should have failed");
        } catch (IllegalStateException e) {
            verifyException(e, "can't allocate");
        }

        // and should be able to get out entries as well:
        long[] data = buf1.getNextEntry();
        assertEquals(32, data.length);

        data = buf2.getNextEntry();
        assertEquals(23, data.length);
    }
}
