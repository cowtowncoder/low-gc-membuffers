package com.fasterxml.util.membuf.longs;

import org.junit.Assert;

import com.fasterxml.util.membuf.*;

public class PeekChunkyLongsTest extends MembufTestBase
{
   public void testSimplePeeks() throws Exception
   {
       _testSimplePeeks(SegType.BYTE_ARRAY);
       _testSimplePeeks(SegType.BYTE_BUFFER_FAKE);
       _testSimplePeeks(SegType.BYTE_BUFFER_DIRECT);
   }

   /*
   /**********************************************************************
   /* Actual test impls
   /**********************************************************************
    */

   private void _testSimplePeeks(SegType aType) throws Exception
   {
       // 10 byte segments, max 4
       final ChunkyLongsMemBuffer buffer = createLongsBuffers(aType, 10, 1, 4).createChunkyBuffer(1, 4);

       // append 6 segments
       for (int i = 1; i <= 6; ++i) {
           buffer.appendEntry(buildLongsChunk(i));
       }
       assertEquals(6, buffer.getEntryCount());
       assertEquals(21, buffer.getTotalPayloadLength());
       assertFalse(buffer.isEmpty());

       // then peek, read/skip
       long[] chunk = buffer.peekNextEntry();
       Assert.assertArrayEquals(buildLongsChunk(1), chunk);
       assertEquals(1, buffer.skipNextEntry());

       assertEquals(2, buffer.getNextEntryLength());
       chunk = buffer.peekNextEntry();
       Assert.assertArrayEquals(buildLongsChunk(2), chunk);
       assertEquals(2, buffer.getNextEntryLength());
       long[] gotten = buffer.getNextEntryIfAvailable();
       Assert.assertArrayEquals(chunk, gotten);

       chunk = buffer.getNextEntryIfAvailable();
       Assert.assertArrayEquals(buildLongsChunk(3), chunk);

       chunk = buffer.peekNextEntry();
       Assert.assertArrayEquals(buildLongsChunk(4), chunk);
       // should be idempotent
       long[] chunk2 = buffer.peekNextEntry();
       Assert.assertArrayEquals(chunk, chunk2);
       assertEquals(4, buffer.skipNextEntry());
       
       assertEquals(5, buffer.skipNextEntry());

       chunk = buffer.peekNextEntry();
       Assert.assertArrayEquals(buildLongsChunk(6), chunk);
       assertEquals(6, buffer.getNextEntryLength());
       long[] buf = new long[6];
       assertEquals(6, buffer.readNextEntry(buf, 0));
       Assert.assertArrayEquals(chunk, buf);

       // and when empty, nothing more:
       assertEquals(-1, buffer.skipNextEntry());
       assertTrue(buffer.isEmpty());

       buffer.close();
   }

}
