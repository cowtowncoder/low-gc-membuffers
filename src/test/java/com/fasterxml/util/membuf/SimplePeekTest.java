package com.fasterxml.util.membuf;

import org.junit.Assert;

public class SimplePeekTest extends MembufTestBase
{
   public void testSimplePeeks() throws Exception
   {
       _testSimplePeeks(Allocator.BYTE_BUFFER_DIRECT);
       _testSimplePeeks(Allocator.BYTE_BUFFER_FAKE);
       _testSimplePeeks(Allocator.BYTE_ARRAY);
   }

   /*
   /**********************************************************************
   /* Actual test impls
   /**********************************************************************
    */

   private void _testSimplePeeks(Allocator aType) throws Exception
   {
       // 10 byte segments, max 4
       final MemBuffers bufs = createBuffers(aType, 10, 1, 4);
       final MemBuffer buffer = bufs.createBuffer(1, 4);

       // append 6 segments
       for (int i = 1; i <= 6; ++i) {
           buffer.appendEntry(buildChunk(i));
       }
       assertEquals(6, buffer.getEntryCount());
       assertEquals(21, buffer.getTotalPayloadLength());
       assertFalse(buffer.isEmpty());

       // then peek, read/skip
       byte[] chunk = buffer.peekNextEntry();
       Assert.assertArrayEquals(buildChunk(1), chunk);
       assertEquals(1, buffer.skipNextEntry());

       assertEquals(2, buffer.getNextEntryLength());
       chunk = buffer.peekNextEntry();
       Assert.assertArrayEquals(buildChunk(2), chunk);
       assertEquals(2, buffer.getNextEntryLength());
       byte[] gotten = buffer.getNextEntryIfAvailable();
       Assert.assertArrayEquals(chunk, gotten);

       chunk = buffer.getNextEntryIfAvailable();
       Assert.assertArrayEquals(buildChunk(3), chunk);

       chunk = buffer.peekNextEntry();
       Assert.assertArrayEquals(buildChunk(4), chunk);
       // should be idempotent
       byte[] chunk2 = buffer.peekNextEntry();
       Assert.assertArrayEquals(chunk, chunk2);
       assertEquals(4, buffer.skipNextEntry());
       
       assertEquals(5, buffer.skipNextEntry());

       chunk = buffer.peekNextEntry();
       Assert.assertArrayEquals(buildChunk(6), chunk);
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
