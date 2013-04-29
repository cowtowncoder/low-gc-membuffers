package com.fasterxml.util.membuf;

import java.lang.ref.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link MemBufferTracker} implementation that adds support for automatic
 * closing of {@link MemBuffer}s (triggered by garbage-collection, by using
 * {@link java.lang.ref.WeakReference}) to either remove need to close them,
 * or to guard against accidental leakage.
 *<p>
 * Instances are registered with buffer factories such as
 * {@link MemBuffersForBytes} and {@link MemBuffersForLongs}.
 *<p>
 * Note that instances are typically not shared between buffer factory
 * instances, although technically they can be -- this is mostly because
 * doing so can become a concurrency bottleneck; and because such reuse
 * does not have real benefits (overhead per tracker is low).
 * 
 * @since 0.9.2
 */
public class AutoClosingTracker
    implements MemBufferTracker
{
    /**
     * We will keep track of number of buffers being tracked; it may be off only
     * due to pending GC cleanup of soft-reachable buffers.
     */
    protected final AtomicInteger _count = new AtomicInteger(0);

    protected final ReferenceQueue<MemBuffer> _deadQ = new ReferenceQueue<MemBuffer>();

    /**
     * Let's keep a logical head of the double-linked queue of buffers;
     * it is an empty "sentinel" token without actual content, and used
     * to simplify handling (no null checks or special needs for head entry)
     */
    protected final TokenImpl _head;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    public AutoClosingTracker()
    {
        // hope it's ok to have 'null' for thing to refer to...
        _head = new TokenImpl(this, _deadQ, null, null);
    }
    
    /*
    /**********************************************************************
    /* MemBufferTracker API implementation
    /**********************************************************************
     */

    @Override
    public int getActiveBufferCount() {
        return _count.get();
    }

    @Override
    public synchronized void clean()
    {
        TokenImpl token;

        while ((token = (TokenImpl) _deadQ.poll()) != null) {
            // this should not yet be cleared should it?
            MemBuffer buffer = token.get();
            buffer.close();
        }
    }

    @Override
    public synchronized Token trackBuffer(MemBuffer b)
    {
        _count.addAndGet(1);
        return new TokenImpl(this, _deadQ, b, _head);
    }

    /*
    /**********************************************************************
    /* Overridable helper methods
    /**********************************************************************
     */

    /**
     * Method that is called after specified buffer has been auto-closed
     * due to it being garbage-collected. Since this implies that buffer
     * has essentially leaked, implementations may want to notify this
     * somehow; default implementation does nothing.
     *<p>
     * Note that this method will be called in synchronized scope
     * (synchronized on this tracker instance).
     *
     * @param buffer Buffer that was automatically closed
     */
    protected void reportAutoClose(MemBuffer buffer)
    {
        // default impl is no-op
    }

    /*
    /**********************************************************************
    /* Helper classes
    /**********************************************************************
     */

    protected static class TokenImpl
        extends SoftReference<MemBuffer>
        implements Token
    {
        protected final AutoClosingTracker tracker;

        protected TokenImpl prev, next;

        public TokenImpl(AutoClosingTracker t, ReferenceQueue<MemBuffer> q, MemBuffer b,
                TokenImpl p)
        {
            super(b, q);
            tracker = t;
            // auto-link as necessary
            if (p == null) {
                prev = null;
                next = null;
            } else {
                next = p.next;
                prev = p;
                if (next != null) {
                    next.prev = this;
                }
                p.next = this;
            }
        }
        
        @Override
        public void bufferClosed()
        {
            // Need to sync to ensure linked list linkage doesn't go corrupt
            synchronized (tracker) {
                tracker._count.addAndGet(-1);
                unlink();
            }
        }

        protected void unlink()
        {
            if (prev != null) {
                prev.next = next;
            }
            if (next != null) {
                next.prev = prev;
            }
            prev = null;
            next = null;
        }
    }
}
