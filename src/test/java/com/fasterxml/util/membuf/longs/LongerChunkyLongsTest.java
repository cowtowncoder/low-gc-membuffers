package com.fasterxml.util.membuf.longs;

import org.junit.Assert;

import com.fasterxml.util.membuf.*;

/**
 * Unit test that uses a sample file, sending all entries, one by
 * one (buffering all), then reading one-by-one and verifying
 * results.
 */
public class LongerChunkyLongsTest extends MembufTestBase
{
    public void test12SegmentBuffer() throws Exception
    {
        _test12SegmentBuffer(SegType.BYTE_BUFFER_DIRECT);
        _test12SegmentBuffer(SegType.BYTE_BUFFER_FAKE);
        _test12SegmentBuffer(SegType.BYTE_ARRAY);
    }
    
    /*
    /**********************************************************************
    /* Actual test impls
    /**********************************************************************
     */
    
    // And then a more mechanical test:
    public void _test12SegmentBuffer(SegType aType) throws Exception
    {
        // 48kB, in 12 x 4kB segments
        ChunkyLongsMemBuffer buffer = createLongsBuffers(aType, 4 * 1024, 4, 12).createChunkyBuffer(5, 12);

        /* should have space for at least 11 * 4 == 44kB at any point;
         * but use uneven length to force boundary conditions.
         */
        final long[] chunk = buildLongsChunk(257);
        final int initialCount = (11 * 4 * 1024) / 259;
        _write(buffer, chunk, initialCount); // 258 per entry due to 2-byte length prefix

        final SegmentAllocator<?> all = buffer.getAllocator();
        // should be close to full, so:
        assertEquals(11, buffer.getSegmentCount());
        assertEquals(11, all.getBufferOwnedSegmentCount());
        // nothing yet returned, hence not 1
        assertEquals(0, all.getReusableSegmentCount());
        
        // and then read some, append some..... one third, say
        final int deltaCount = initialCount / 3;
        _read(buffer, chunk, deltaCount);

        assertEquals(8, buffer.getSegmentCount());
        assertEquals(8, all.getBufferOwnedSegmentCount());
        // somewhat arbitrary, depends on boundary: 2 or 3
        assertEquals(3, all.getReusableSegmentCount());
        
        _write(buffer, chunk, deltaCount);
        _read(buffer, chunk, deltaCount);
        _write(buffer, chunk, deltaCount);
        // read to almost empty:
        _read(buffer, chunk, deltaCount);
        _read(buffer, chunk, deltaCount);
        _read(buffer, chunk, deltaCount);
        _write(buffer, chunk, deltaCount);
        _read(buffer, chunk, deltaCount);

        // and then read the remainder
        int count = _skipAll(buffer);
        assertEquals(2, count);
        assertEquals(0, buffer.getEntryCount());

        // should be 1 or 2 when empty
        assertEquals(1, buffer.getSegmentCount());
        assertEquals(5, all.getBufferOwnedSegmentCount());
        assertEquals(4, all.getReusableSegmentCount());

        // and finally, let's fully fill up again
        _write(buffer, chunk, initialCount);
        assertEquals(12, buffer.getSegmentCount());
        assertEquals(12, all.getBufferOwnedSegmentCount());
        assertEquals(0, all.getReusableSegmentCount());
    }
    
    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */

    private int _skipAll(ChunkyMemBuffer buffer)
    {
        int count = 0;
        
        while (buffer.skipNextEntry() >= 0) {
            ++count;
        }
        return count;
    }
    
    private void _write(ChunkyLongsMemBuffer buffer, long[] chunk, int count) throws Exception
    {
        final int initialCount = buffer.getEntryCount();
        final long initialLength = buffer.getTotalPayloadLength();
        for (int i = 0; i < count; ++i) {
            if (!buffer.tryAppendEntry(chunk)) {
                fail("Failed to append; i = "+i+" / "+count);
            }
        }
        assertEquals(initialCount + count, buffer.getEntryCount());
        assertEquals(initialLength + (count * chunk.length), buffer.getTotalPayloadLength());
    }

    private void _read(ChunkyLongsMemBuffer buffer, long[] chunk, int count) throws Exception
    {
        final int initialCount = buffer.getEntryCount();
        final long initialLength = buffer.getTotalPayloadLength();
        for (int i = 0; i < count; ++i) {
            long[] next = buffer.getNextEntry(1L);
            Assert.assertArrayEquals(chunk, next);
        }
        assertEquals(initialCount - count, buffer.getEntryCount());
        assertEquals(initialLength - (count * chunk.length), buffer.getTotalPayloadLength());
    }
}
