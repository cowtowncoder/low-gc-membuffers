package com.fasterxml.util.membuf.misc;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

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

    static class WeakRefBuffer extends ChunkyBytesMemBufferImpl
    {
        // yeah, this is pretty weak!
        protected final WeakReference<WeakRefBuffer> _weak;
        
        public WeakRefBuffer(ChunkyBytesMemBuffer src, ReferenceQueue<WeakRefBuffer> q) {
            super(src);
            _weak = new ClosingWeakRef(this, q);
        }
    }

    static class ClosingWeakRef extends WeakReference<WeakRefBuffer>
    {
        public ClosingWeakRef(WeakRefBuffer referent, ReferenceQueue<WeakRefBuffer> q) {
            super(referent, q);
System.out.println("createWeak");            
        }
        
        public boolean enqueue() {
System.out.println("enq!");
            get().close();
            return super.enqueue();
        }
    }
    
    static class MyWeakCleaner extends ReferenceQueue<WeakRefBuffer> { }
    
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

    /**
     * Alternative implementation that uses {@link WeakReference}s to handle
     * auto-closing.
     */
    /*
    public void testAutoCloseWithWeakRefs() throws Exception
    {
        MemBuffersForBytes factory = new MemBuffersForBytes(ArrayBytesSegment.allocator(1000, 0, 100));
        final MyWeakCleaner cleaner = new MyWeakCleaner();
        factory = factory.withChunkyDecorator(new MemBufferDecorator<ChunkyBytesMemBuffer>() {

            @Override
            public ChunkyBytesMemBuffer decorateMemBuffer(
                    ChunkyBytesMemBuffer original) {
//System.out.println("Created...");
                return new WeakRefBuffer(original, cleaner);
            }
        });
        
        // allocate, drop, 100 buffers, should be fine
        for (int i = 0; i < 50; ++i) {
            factory.createChunkyBuffer(2, 10);
        }

        // force GC
        // and then process the weak queue

        Thread.sleep(50L);
        System.gc();
        Thread.sleep(50L);
        System.gc();
        Thread.sleep(50L);
        
        int count = 0;

        assertNotNull(cleaner.poll());
        
        Reference<? extends WeakRefBuffer> ref;
        while ((ref = cleaner.remove(10L)) != null) {
            ++count;
            ref.get().close();
        }
        if (count == 0) {
            fail("Nothing cleaned");
        }
        assertNotNull(factory.tryCreateChunkyBuffer(2, 10));
    }
*/
}
