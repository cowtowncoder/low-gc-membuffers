package com.fasterxml.util.membuf.bytes;

import org.junit.Assert;

import com.fasterxml.util.membuf.ChunkyBytesMemBuffer;
import com.fasterxml.util.membuf.MembufTestBase;

public class SimplePeekTest extends MembufTestBase
{
   public void testChunkyPeeks() throws Exception
   {
       _testChunkyPeeks(SegType.BYTE_BUFFER_DIRECT);
       _testChunkyPeeks(SegType.BYTE_BUFFER_FAKE);
       _testChunkyPeeks(SegType.BYTE_ARRAY);
   }

   /*
   /**********************************************************************
   /* Actual test impls
   /**********************************************************************
    */

   private void _testChunkyPeeks(SegType aType) throws Exception
   {
       // 10 byte segments, max 4
       final ChunkyBytesMemBuffer buffer = createBytesBuffers(aType, 10, 1, 4).createChunkyBuffer(1, 4);

       // append 6 segments
       for (int i = 1; i <= 6; ++i) {
           buffer.appendEntry(buildBytesChunk(i));
       }
       assertEquals(6, buffer.getEntryCount());
       assertEquals(21, buffer.getTotalPayloadLength());
       assertFalse(buffer.isEmpty());

       // then peek, read/skip
       byte[] chunk = buffer.peekNextEntry();
       Assert.assertArrayEquals(buildBytesChunk(1), chunk);
       assertEquals(1, buffer.skipNextEntry());

       assertEquals(2, buffer.getNextEntryLength());
       chunk = buffer.peekNextEntry();
       Assert.assertArrayEquals(buildBytesChunk(2), chunk);
       assertEquals(2, buffer.getNextEntryLength());
       byte[] gotten = buffer.getNextEntryIfAvailable();
       Assert.assertArrayEquals(chunk, gotten);

       chunk = buffer.getNextEntryIfAvailable();
       Assert.assertArrayEquals(buildBytesChunk(3), chunk);

       chunk = buffer.peekNextEntry();
       Assert.assertArrayEquals(buildBytesChunk(4), chunk);
       // should be idempotent
       byte[] chunk2 = buffer.peekNextEntry();
       Assert.assertArrayEquals(chunk, chunk2);
       assertEquals(4, buffer.skipNextEntry());
       
       assertEquals(5, buffer.skipNextEntry());

       chunk = buffer.peekNextEntry();
       Assert.assertArrayEquals(buildBytesChunk(6), chunk);
       assertEquals(6, buffer.getNextEntryLength());
       byte[] buf = new byte[6];
       assertEquals(6, buffer.readNextEntry(buf, 0));
       Assert.assertArrayEquals(chunk, buf);

       // and when empty, nothing more:
       assertEquals(-1, buffer.skipNextEntry());
       assertTrue(buffer.isEmpty());

       buffer.close();
   }

}
