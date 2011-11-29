package com.fasterxml.membuf;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

import org.junit.Assert;

/**
 * Unit test that uses a sample file, sending all entries, one by
 * one (buffering all), then reading one-by-one and verifying
 * results.
 */
public class TestLonger extends MembufTestBase
{
    final Charset ENCODING = Charset.forName("ISO-8859-1");
    
    public void testShakespeareLineByLine() throws Exception
    {
        // First, read the data
        BufferedReader br = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/hamlet.xml"), ENCODING));
        List<byte[]> rows = readRows(br);
        br.close();

        /* Input file is about 280k; assume modest overhead (even if
         * entries are lines, relatively short). So, 330k should
         * be enough; use 11 buffers of 30k each (one more than
         * absolutely needed)
         */
        MemBuffers bufs = new MemBuffers(30 * 1024, 2, 11);
        MemBuffer buffer = bufs.createBuffer(2, 11);

        // then append/remove three times
        appendAndRemove(rows, buffer);
    }

    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */

    protected List<byte[]> readRows(BufferedReader br) throws IOException
    {
        ArrayList<byte[]> lines = new ArrayList<byte[]>(1000);
        String line;
        while ((line = br.readLine()) != null) {
            lines.add(line.getBytes(ENCODING));
        }
        return lines;
    }

    protected void appendAndRemove(List<byte[]> rows, MemBuffer buffer)
        throws InterruptedException
    {
        long totalPayload = 0L;
        for (byte[] b : rows) {
            totalPayload += b.length;
            buffer.appendEntry(b);
            assertEquals(totalPayload, buffer.getTotalPayloadLength());
        }

        // ok: should have added enough; verify book-keeping
        assertEquals(rows.size(), buffer.getEntryCount());
        assertEquals(totalPayload, buffer.getTotalPayloadLength());
        // we measured that it will take 10 segments for this data
        assertEquals(10, buffer.getSegmentCount());

        /*
        System.err.println("DEBUG: read "+lines.size()+"; space left = "+buffer.getMaximumAvailableSpace()
                +"; paylad = "+buffer.getTotalPayloadLength()
                +", segments = "+buffer.getSegmentCount()
                );
                */

        // then read, verify ordering, invariants:
        Iterator<byte[]> it = rows.iterator();
        int left = rows.size();
        while (it.hasNext()) {
            byte[] b = it.next();
            // pass timeout only to prevent infinite wait in case of a bug
            byte[] actual = buffer.getNextEntry(100L);
            Assert.assertArrayEquals(b, actual);
            totalPayload -= b.length;
            assertEquals(totalPayload, buffer.getTotalPayloadLength());
            --left;
            assertEquals(left, buffer.getEntryCount());
        }
        // All done, should be empty...
        assertEquals(0, buffer.getEntryCount());
        assertEquals(0L, buffer.getTotalPayloadLength());
        // always have at least one segment
        assertEquals(1, buffer.getSegmentCount());
    }

}
