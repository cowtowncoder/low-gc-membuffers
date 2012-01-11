package com.fasterxml.util.membuf.bytes;

import java.util.Arrays;

import org.junit.Assert;

import com.fasterxml.util.membuf.*;

/**
 * Tests to verify handling of case where memory buffers are filled up,
 * for both chunky and streamy byte-valued memory buffers.
 */
public class FullBytesBufferTest extends MembufTestBase
{
    // Test for handling of full buffer
    public void testChunkyTryWriteToFull() throws Exception
    {
        _testChunkyTryWriteToFull(SegType.BYTE_BUFFER_DIRECT);
        _testChunkyTryWriteToFull(SegType.BYTE_BUFFER_FAKE);
        _testChunkyTryWriteToFull(SegType.BYTE_ARRAY);
    }

    // Test for verifying that number of buffers that we can create is also bound.
    public void testChunkyMaxBuffers() throws Exception
    {
        _testChunkyMaxBuffers(SegType.BYTE_BUFFER_DIRECT);
        _testChunkyMaxBuffers(SegType.BYTE_BUFFER_FAKE);
        _testChunkyMaxBuffers(SegType.BYTE_ARRAY);
    }

    // // // Variants for streamy buffers

    public void testStreamyTryWriteToFull() throws Exception
    {
        _testStreamyTryWriteToFull(SegType.BYTE_BUFFER_DIRECT);
        _testStreamyTryWriteToFull(SegType.BYTE_BUFFER_FAKE);
        _testStreamyTryWriteToFull(SegType.BYTE_ARRAY);
    }

    // Test for verifying that number of buffers that we can create is also bound.
    public void testStreamyMaxBuffers() throws Exception
    {
        _testStreamyMaxBuffers(SegType.BYTE_BUFFER_DIRECT);
        _testStreamyMaxBuffers(SegType.BYTE_BUFFER_FAKE);
        _testStreamyMaxBuffers(SegType.BYTE_ARRAY);
    }
    
    /*
    /**********************************************************************
    /* Actual test impls, chunky buffers
    /**********************************************************************
     */
    
    public void _testChunkyTryWriteToFull(SegType aType) throws Exception
    {
        // up to 24 bytes of room (and 12 guaranteed)
        final MemBuffersForBytes bufs = createBytesBuffers(aType, 12, 1, 2);
        final ChunkyBytesMemBuffer buffer = bufs.createChunkyBuffer(1, 2);
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
            fail("Append should have failed");
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

    public void _testChunkyMaxBuffers(SegType aType) throws Exception
    {
        // with max 5 segments, each buffer requiring at least two, can create two
        final MemBuffersForBytes bufs = createBytesBuffers(aType, 12, 1, 5);
        final ChunkyBytesMemBuffer buf1 = bufs.createChunkyBuffer(2, 4);
        assertNotNull(buf1);
        ChunkyBytesMemBuffer buf2 = bufs.createChunkyBuffer(2, 4);
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
        buf1.appendEntry(new byte[32]); // needs 33 bytes
        // but not with additional expansion
        assertFalse(buf1.tryAppendEntry(new byte[4]));

        // and for second buffer can full up to 24 bytes:
        buf2.appendEntry(new byte[23]);
        // but not for more
        try {
            buf2.appendEntry(new byte[5]);
            fail("Append should have failed");
        } catch (IllegalStateException e) {
            verifyException(e, "can't allocate");
        }

        // and should be able to get out entries as well:
        byte[] data = buf1.getNextEntry();
        assertEquals(32, data.length);

        data = buf2.getNextEntry();
        assertEquals(23, data.length);
    }

    /*
    /**********************************************************************
    /* Actual test impls, streamy buffers
    /**********************************************************************
     */

    public void _testStreamyTryWriteToFull(SegType aType) throws Exception
    {
        // up to 24 bytes of room (and 12 guaranteed)
        final MemBuffersForBytes bufs = createBytesBuffers(aType, 12, 1, 2);
        final StreamyBytesMemBuffer buffer = bufs.createStreamyBuffer(1, 2);
        byte[] data = new byte[20];
        Arrays.fill(data, (byte) 0xFF);

        buffer.append(data, 0, 17);
        assertEquals(17L, buffer.getTotalPayloadLength());
        // 17 bytes used so 7 left:
        assertEquals(7L, buffer.getMaximumAvailableSpace());

        // so far so good; but won't have room for another one
        assertFalse(buffer.tryAppend(data));
        // and if do without try, should get an exception
        try {
            buffer.append(data);
            fail("Append should have failed");
        } catch (IllegalStateException e) {
            verifyException(e, "can't allocate");
        }

        // state should be fine however:
        byte[] result = new byte[20];
        assertEquals(17, buffer.read(result));
        for (int i = 0; i < 17; ++i) {
            assertEquals((byte) 0xFF, result[i]);
        }
        
        assertEquals(0L, buffer.getTotalPayloadLength());
        /* won't have full 24 due to alignments; will have 7 from
         * partial buffer, 12 from the other one, for total of 19
         */
        assertEquals(19L, buffer.getMaximumAvailableSpace());

        // and 19 should be enough as we only need 17
        buffer.append(data, 3, 17);
        assertEquals(17L, buffer.getTotalPayloadLength());
    }

    public void _testStreamyMaxBuffers(SegType aType) throws Exception
    {
        // with max 5 segments, each buffer requiring at least two, can create two
        final MemBuffersForBytes bufs = createBytesBuffers(aType, 12, 1, 5);
        final StreamyBytesMemBuffer buf1 = bufs.createStreamyBuffer(2, 4);
        assertNotNull(buf1);
        StreamyBytesMemBuffer buf2 = bufs.createStreamyBuffer(2, 4);
        assertNotNull(buf2);

        // and then we should fail:
        try {
            bufs.createStreamyBuffer(2, 4);
            fail("Buffer creation should have failed");
        } catch (IllegalStateException e) {
            verifyException(e, "due to segment allocation limits");
        }
        assertNull(bufs.tryCreateStreamyBuffer(2, 4));

        // furthermore, should be able to extend one of buffers by one segment:
        buf1.append(new byte[33]);
        // but not with additional expansion
        assertFalse(buf1.tryAppend(new byte[4]));

        // and for second buffer can full up to 24 bytes:
        buf2.append(new byte[23]);
        // but not for more
        try {
            buf2.append(new byte[5]);
            fail("Append should have failed");
        } catch (IllegalStateException e) {
            verifyException(e, "can't allocate");
        }

        // and should be able to get out entries as well:
        byte[] result = new byte[100];
        assertEquals(33, buf1.read(result));
        assertEquals(0L, buf1.getTotalPayloadLength());
        assertEquals(0, buf1.readIfAvailable(result));

        assertEquals(23, buf2.read(result));
        assertEquals(0L, buf2.getTotalPayloadLength());
        assertEquals(0, buf2.readIfAvailable(result));
    }
}
