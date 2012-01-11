package com.fasterxml.util.membuf.bytes;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

import org.junit.Assert;

import com.fasterxml.util.membuf.*;

/**
 * Unit test copied from matching bytes-test; simply coerces bytes
 * to longs, which should work ok, if not perfectly...
 */
public class LongerStreamyBytesTest  extends MembufTestBase
{
    final Charset ENCODING = Charset.forName("ISO-8859-1");

    public void testShakespeare() throws Exception
    {
        _testShakespeare(SegType.BYTE_BUFFER_DIRECT);
        _testShakespeare(SegType.BYTE_BUFFER_FAKE);
        _testShakespeare(SegType.BYTE_ARRAY);
    }

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
    
    private void _testShakespeare(SegType aType) throws Exception
    {
        // First, read the data
        BufferedReader br = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/hamlet.xml"), ENCODING));
        List<long[]> rows = readRows(br);
        br.close();

        /* Input file is about 280k, so 300k should
         * be enough; use 10 buffers of 30k each (one more than
         * absolutely needed)
         */
        StreamyLongsMemBuffer buffer = createLongsBuffers(aType, 30 * 1024, 2, 10).createStreamyBuffer(8, 10);

        // then append/remove multiple times
        appendAndRemove(rows, buffer, 9);
        appendAndRemove(rows, buffer, 10);
        appendAndRemove(rows, buffer, 10);
        appendAndRemove(rows, buffer, 10);
        appendAndRemove(rows, buffer, 10);

        // then try appends with partial read, clear
        appendAndClear(rows, buffer, 9);

        // and repeat once for bot
        appendAndRemove(rows, buffer, 9);
        appendAndClear(rows, buffer, 10);
    }

    // And then a more mechanical test:
    public void _test12SegmentBuffer(SegType aType) throws Exception
    {
        // 48kB, in 12 x 4kB segments
        StreamyLongsMemBuffer buffer = createLongsBuffers(aType, 4 * 1024, 4, 12).createStreamyBuffer(5, 12);

        /* should have space for at least 11 * 4 == 44kB at any point;
         * but use uneven length to force boundary conditions.
         */
        final long[] chunk = buildLongsChunk(257);
        final int initialCount = (11 * 4 * 1024) / 259;
        _write(buffer, chunk, initialCount); // 258 per entry due to 2-long length prefix

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

        int expLen = (int) buffer.getTotalPayloadLength();
        
        // and then read the remainder
        int skipped = _skipAll(buffer);
        assertEquals(expLen, skipped);

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

    protected List<long[]> readRows(BufferedReader br) throws IOException
    {
        ArrayList<long[]> lines = new ArrayList<long[]>(1000);
        String line;
        while ((line = br.readLine()) != null) {
            byte[] b = line.getBytes(ENCODING);
            long[] l = new long[b.length];
            for (int i = 0; i < b.length; ++i) {
                l[i] = (long) b[i];
            }
            lines.add(l);
        }
        return lines;
    }

    protected void appendAndRemove(List<long[]> rows, StreamyLongsMemBuffer buffer, int expSegs)
        throws InterruptedException
    {
        long totalPayload = 0L;
        for (long[] b : rows) {
            totalPayload += b.length;
            buffer.append(b);
            assertEquals(totalPayload, buffer.getTotalPayloadLength());
        }

        // ok: should have added enough; verify book-keeping
        assertEquals(totalPayload, buffer.getTotalPayloadLength());
        // we measured that it will take 9 segments for this data
        assertEquals(expSegs, buffer.getSegmentCount());

        // then read, verify ordering, invariants:
        Iterator<long[]> it = rows.iterator();
        while (it.hasNext()) {
            long[] line = it.next();
            // pass timeout only to prevent infinite wait in case of a bug
            long[] lineBuf = new long[line.length];
            int len = buffer.read(10L, lineBuf, 0, lineBuf.length);
            assertEquals(lineBuf.length, len);
            Assert.assertArrayEquals(line, lineBuf);
            totalPayload -= line.length;
            assertEquals(totalPayload, buffer.getTotalPayloadLength());
        }
        // All done, should be empty...
        assertEquals(0L, buffer.getTotalPayloadLength());
        // always have at least one segment
        assertEquals(1, buffer.getSegmentCount());
    }

    protected void appendAndClear(List<long[]> rows, StreamyLongsMemBuffer buffer, int expSegCount)
        throws InterruptedException
    {
        long totalPayload = 0L;
        for (long[] b : rows) {
            totalPayload += b.length;
            buffer.append(b);
            assertEquals(totalPayload, buffer.getTotalPayloadLength());
        }

        assertEquals(totalPayload, buffer.getTotalPayloadLength());
        assertEquals(expSegCount, buffer.getSegmentCount());

        // then only read first 5 lines
        for (int i = 0; i < 5; ++i) {
            long[] exp = rows.get(i);
            long[] lineBuf = new long[exp.length];
            assertEquals(exp.length, buffer.readIfAvailable(lineBuf));
            Assert.assertArrayEquals(exp, lineBuf);
            totalPayload -= exp.length;
            assertEquals(totalPayload, buffer.getTotalPayloadLength());
        }
        // then clear again, verify it's empty...
        buffer.clear();
        assertEquals(0L, buffer.getTotalPayloadLength());
        // always have at least one segment
        assertEquals(1, buffer.getSegmentCount());
    }

    private int _skipAll(StreamyMemBuffer buffer)
    {
        int total = 0;
        int count;
        
        while ((count = buffer.skip(1000)) > 0) {
            total += count;
        }
        return total;
    }

    private void _write(StreamyLongsMemBuffer buffer, long[] chunk, int count) throws Exception
    {
        final long initialLength = buffer.getTotalPayloadLength();
        for (int i = 0; i < count; ++i) {
            if (!buffer.tryAppend(chunk)) {
                fail("Failed to append; i = "+i+" / "+count);
            }
        }
        assertEquals(initialLength + (count * chunk.length), buffer.getTotalPayloadLength());
    }

    private void _read(StreamyLongsMemBuffer buffer, long[] chunk, int count) throws Exception
    {
        final long initialLength = buffer.getTotalPayloadLength();
        long[] readBuf = new long[chunk.length + 10];
        for (int i = 0; i < count; ++i) {
            int len = buffer.readIfAvailable(readBuf, 0, chunk.length);
            assertEquals(chunk.length, len);
            _assertPrefix(chunk, readBuf, chunk.length);
        }
        assertEquals(initialLength - (count * chunk.length), buffer.getTotalPayloadLength());
    }

    private void _assertPrefix(long[] exp, long[] act, int len)
    {
        for (int i = 0; i < len; ++i) {
            if (exp[i] != act[i]) {
                fail("Arrays differ at #"+i+" (of "+len+")");
            }
        }
    }
}
