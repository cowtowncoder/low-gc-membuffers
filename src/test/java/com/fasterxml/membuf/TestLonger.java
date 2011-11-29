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
        /* Input file is about 280k; assume +40% overhead (since
         * entries are lines, relatively short). So, 400k should
         * be enough; use 11 buffers of 40k each (one more than
         * absolutely needed)
         */
        MemBuffers bufs = new MemBuffers(40 * 1024, 2, 11);
        MemBuffer buffer = bufs.createBuffer(2, 11);

        BufferedReader br = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/hamlet.xml"), ENCODING));
        ArrayList<String> lines = new ArrayList<String>(1000);
        String line;
        int totalPayload = 0;
        
        while ((line = br.readLine()) != null) {
            lines.add(line);
            byte[] b = line.getBytes(ENCODING);
            totalPayload += b.length;
            buffer.appendEntry(b);
        }

        // ok: should have added enough; verify book-keeping
        assertEquals(lines.size(), buffer.getEntryCount());
        assertEquals(totalPayload, buffer.getTotalPayloadLength());

        /*
        System.err.println("DEBUG: read "+lines.size()+"; space left = "+buffer.getMaximumAvailableSpace()
                +"; paylad = "+buffer.getTotalPayloadLength()
                +", segments = "+buffer.getSegmentCount()
                );
                */

        // then read, verify ordering, invariants:
        Iterator<String> it = lines.iterator();
        int left = lines.size();
        while (it.hasNext()) {
            line = it.next();
            byte[] b = line.getBytes(ENCODING);
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
        assertEquals(2, buffer.getSegmentCount());
    }

}
