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
    /* Test methods
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
}
