package com.fasterxml.util.membuf;

import java.io.*;

/*
 * Copyright Tatu Saloranta, 2011-
 */

/**
 * Class that defines generic memory buffer interface.
 * Memory buffers are extendable linear FIFO data structures.
 * Since this base type does not expose all access (mostly because
 * generic typing does not work for primitive values), it is
 * not commonly used, but it may be useful for printing diagnostics
 * or some synchronization.
 *<p>
 * While not mandated by the interface, existing implementations all use a
 * set of {@link Segment}s as virtual ring buffer.
 * Number of segments used is bound by minimum and maximum
 * amounts, which defines minimum and maximum memory usage.
 * Memory usage is relatively easy to estimate since data is stored as
 * linear sequences (arrays or <code>XxxBuffer</code>s (like <code>ByteBuffer</code>)
 * having very little overhead.
 * 
 * @author Tatu Saloranta
 */
public interface MemBuffer
    extends Closeable // just for convenience
    // and, in future, AutoCloseable?
{
    /*
    /**********************************************************************
    /* Public API, simple statistics (not data) accessors
    /**********************************************************************
     */

    /**
     * Method for checking whether this buffer has no entries,
     * functionally equivalent to:
     *<pre>
     *   getEntryCount() == 0
     *</pre>
     * 
     * @return True if this buffer contains no entries
     */
    public abstract boolean isEmpty();
    
    /**
     * Method for checking how many segments are currently used for
     * storing data (not including segments that may be retained
     * for reuse but do not contain data)
     */
    public abstract int getSegmentCount();

    /**
     * Method for checking what would be the maximum available space
     * available for appending more entries. Since there may be per-entry
     * overhead (length prefix of at least one and at most five bytes),
     * this is not necessarily all available for payload, but gives
     * an upper-bound estimate.
     */
    public abstract long getMaximumAvailableSpace();

    /**
     * Method for checking total amount of payload buffered in this buffer.
     * This does not include entry metadata (length prefixes), if any.
     */
    public abstract long getTotalPayloadLength();

    /*
    /**********************************************************************
    /* Public API, state changes
    /**********************************************************************
     */

    /**
     * Method that will discard contents of this buffer; functionally similar
     * to just skipping all contents, but is more efficient and guaranteed
     * to be atomic operation.
     */
    public abstract void clear();

    /**
     * Method that can be used to clean up resources (segments
     * allocated) this buffer is using, necessary step to take
     * for unused instances in case underlying storage uses
     * off-heap memory (such as direct-allocated {@link java.nio.ByteBuffer}s).
     */
    @Override // from Closeable -- note, does NOT throw IOException
    public abstract void close();    

    /*
    /**********************************************************************
    /* Public API: type-independent data access
    /**********************************************************************
     */
    
    /**
     * Method that can be called to wait until buffer is not empty, that is,
     * it has some data (entry or value sequence) to read.
     *<p>
     * Note that it is possible to have a race condition if there are
     * multiple readers, such that even if this method returns, following
     * read could fail/block (when another reader thread manages to thread
     * before current thread reads next entry); this method can only be
     * used to guarantee data for single-threaded reads, although it may
     * work as an optimization for multiple reader case as well.
     */
    public abstract void waitUntilNotEmpty() throws InterruptedException;

    /**
     * Method that can be called to wait until buffer is not empty, that is,
     * it has some data (entry or value sequence) to read,
     * but to only wait up until specified
     * time has elapsed. Note that wait time is lower-bound and actual
     * wait may be longer, depending on things like timer resolution;
     * this depends on how accurately {@link Object#wait(long)} limits
     * wait time.
     *<p>
     * Note that it is possible to have a race condition if there are
     * multiple readers, such that even if this method returns, following
     * read could fail/block (when another reader thread manages to thread
     * before current thread reads next entry); this method can only be
     * used to guarantee data for single-threaded reads, although it may
     * work as an optimization for multiple reader case as well.
     */
    public abstract void waitUntilNotEmpty(long maxWaitMsecs) throws InterruptedException;
}
