package com.fasterxml.util.membuf.misc;

import com.fasterxml.util.membuf.*;
import com.fasterxml.util.membuf.impl.ArrayBytesSegment;
import com.fasterxml.util.membuf.impl.ChunkyBytesMemBufferImpl;

/**
 * Ability to auto-close buffers is sometimes handy; and to make this
 * easier, as an orthogonal facility, one can use decorators.
 */
public class TestAutoClose extends MembufTestBase
{
    static class AutoClosingBuffer extends ChunkyBytesMemBufferImpl
    {
        public AutoClosingBuffer(ChunkyBytesMemBuffer src) {
            super(src);
        }
        
        @Override
        public void finalize()
        {
            close();
        }
    }
    
    /*
    /**********************************************************************
    /* Test methods: using decorator
    /**********************************************************************
     */

    public void testAutoCloseWithFinalize() throws Exception
    {
        // First, verify invariant that we'll run out of segments if
        // buffers are not closed

        // use byte-array backed one
        MemBuffersForBytes factory = new MemBuffersForBytes(ArrayBytesSegment.allocator(1000, 0, 100));
        
        // allocate, drop, 100 buffers, should be fine
        for (int i = 0; i < 50; ++i) {
            factory.createChunkyBuffer(2, 10);
        }
        // and this should fail
        assertNull(factory.tryCreateChunkyBuffer(2, 10));
        
        // But: let's fix the issue by auto-closing
        factory = new MemBuffersForBytes(ArrayBytesSegment.allocator(1000, 0, 100));
        factory = factory.withChunkyDecorator(new MemBufferDecorator<ChunkyBytesMemBuffer>() {

            @Override
            public ChunkyBytesMemBuffer decorateMemBuffer(
                    ChunkyBytesMemBuffer original) {
                return new AutoClosingBuffer(original);
            }
            
        });
        for (int i = 0; i < 50; ++i) {
            factory.createChunkyBuffer(2, 10);
        }
        // important: force GC
        System.gc();
        Thread.sleep(50L);

        assertNotNull(factory.tryCreateChunkyBuffer(2, 10));
    }

    /*
    /**********************************************************************
    /* Test methods: using MemBufferTracker
    /**********************************************************************
     */
    
    public void testAutoCloseWithTracker() throws Exception
    {
        // First, verify that we couldn't get more buffers if references still active
        // buffers are not closed

        // use byte-array backed one
        AutoClosingTracker tracker = new AutoClosingTracker();
        MemBuffersForBytes factory = new MemBuffersForBytes(ArrayBytesSegment.allocator(1000, 0, 5))
            .withTracker(tracker);

        ChunkyBytesMemBuffer buf1 = factory.createChunkyBuffer(2, 10);
        assertNotNull(buf1);
        ChunkyBytesMemBuffer buf2 = factory.createChunkyBuffer(2, 10);
        assertNotNull(buf2);
        assertEquals(2, tracker.getActiveBufferCount());

        // and this should fail
        assertNull(factory.tryCreateChunkyBuffer(2, 10));
        System.gc();
        Thread.sleep(20L);
        assertNull(factory.tryCreateChunkyBuffer(2, 10));

        // but if we drop the refs and let GC take its course...
        buf1 = buf2 = null;
        System.gc();
        Thread.sleep(20L);
        // ... not yet, without cleanup (no backgorund threads) or attempt at realloc
        assertEquals(2, tracker.getActiveBufferCount());
        tracker.clean();
        assertEquals(0, tracker.getActiveBufferCount());

        // and then can alloc more:
        buf1 = factory.createChunkyBuffer(2, 10);
        assertNotNull(buf1);
        assertEquals(1, tracker.getActiveBufferCount());

        // also: verify that explicit close works too:
        buf1.close();
        assertEquals(0, tracker.getActiveBufferCount());
        System.gc();
        Thread.sleep(20L);
        tracker.clean();
        System.gc();
        Thread.sleep(20L);
        assertEquals(0, tracker.getActiveBufferCount());
    }

}
