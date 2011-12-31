package com.fasterxml.util.membuf;

import java.io.*;

/*
 * Copyright Tatu Saloranta, 2011-
 */

/**
 * Actual memory queue implementation, which uses set of {@link Segment}s as
 * virtual ring buffer. Number of segments used is bound by minimum and maximum
 * amounts, which defines minimim and maximum memory usage.
 * Memory usage is relatively easy to estimate since data is stored as
 * byte sequences and almost all memory is simply used by
 * allocated <code>ByteBuffer</code>s.
 *<p>
 * Access to queue is fully synchronized -- meaning that all methods are
 * synchronized by implementations as necessary, and caller should not need
 * to use external synchronization -- since parts will have to be anyway
 * (updating of stats, pointers), and since all real-world use cases will
 * need some level of synchronization anyway, even with just single producer
 * and consumer. If it turns out that there are bottlenecks that could be
 * avoided with more granular (or external) locking, this design can be
 * revisited.
 *<p>
 * Note that if instances are discarded, they <b>MUST</b> be closed:
 * finalize() method is not implemented since it is both somewhat unreliable
 * (i.e. should not be counted on) and can add overhead for GC processing.
 * 
 * @author Tatu Saloranta
 */
public abstract class MemBuffer
    implements Closeable // just for convenience
{
    /*
    /**********************************************************************
    /* Public API, simple statistics (not data) accessors
    /**********************************************************************
     */

    /**
     * Method for checking how many entries are buffered in this buffer
     * currently.
     */
    public abstract int getEntryCount();

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
     * available for appending more entries. Since there is per-entry
     * overhead (length prefix of at least one and at most five bytes),
     * this is not all available for entries, but gives an approximate
     * idea of that amount.
     */
    public abstract long getMaximumAvailableSpace();

    /**
     * Method for checking total amount of payload buffered in this buffer.
     * This does not include entry metadata (length prefixes).
     */
    public abstract long getTotalPayloadLength();

    /*
    /**********************************************************************
    /* Public API, state changes
    /**********************************************************************
     */

    /**
     * Method that will discard contents of this buffer; functionally similar
     * to just reading all contents, but is more efficient and guaranteed
     * to be atomic operation.
     */
    public abstract void clear();

    /**
     * Method that can be used to clean up resources (segments
     * allocated) this buffer is using, necessary step to take
     * for unused instances in case underlying storage uses
     * off-heap memory (such as direct-allocated {@link ByteBuffer}s).
     */
    @Override // from Closeable -- note, does NOT throw IOException
    public abstract void close();
    
    /*
    /**********************************************************************
    /* Public API, write (append)
    /**********************************************************************
     */

    /**
     * Method that tries to append an entry in buffer and returning;
     * if there is no room, a {@link IllegalStateException} is thrown.
     */
    public abstract void appendEntry(byte[] data);

    /**
     * Method that tries to append an entry in buffer and returning;
     * if there is no room, a {@link IllegalStateException} is thrown.
     */
    public abstract void appendEntry(byte[] data, int dataOffset, int dataLength);

    /**
     * Method that tries to append an entry in buffer if there is enough room;
     * if there is, entry is appended and 'true' returned; otherwise no changes
     * are made and 'false' is returned.
     */
    public abstract boolean tryAppendEntry(byte[] data);
    
    /**
     * Method that tries to append an entry in buffer if there is enough room;
     * if there is, entry is appended and 'true' returned; otherwise no changes
     * are made and 'false' is returned.
     */
    public abstract boolean tryAppendEntry(byte[] data, int dataOffset, int dataLength);
    
    /*
    /**********************************************************************
    /* Public API, reading
    /**********************************************************************
     */

    /**
     * Method that will check size of the next entry, if buffer has entries;
     * returns size in bytes if there is at least one entry, or -1 if buffer
     * is empty.
     */
    public abstract int getNextEntryLength();

    /**
     * Method for reading and removing next available entry from buffer.
     * If no entry is available, will block to wait for more data.
     */
    public abstract byte[] getNextEntry() throws InterruptedException;

    /**
     * Method that will read, remove and return next entry, if one is
     * available; or return null if not.
     */
    public abstract byte[] getNextEntryIfAvailable();
    
    /**
     * Method to get (and remove) next entry from the buffer, if one
     * is available. If buffer is empty, may wait up to specified amount
     * of time for new data to arrive.
     * 
     * @param timeoutMsecs Amount of time to wait for more data if
     *   buffer is empty, if non-zero positive number; if zero or
     *   negative number, will return immediately
     *   
     * @return Next entry from buffer, if one was available either
     *   immediately or before waiting for full timeout; or null
     *   if no entry became available
     */
    public abstract byte[] getNextEntry(long timeoutMsecs) throws InterruptedException;

    /**
     * Method that will skip the next entry from the buffer, if an entry
     * is available, and return length of the skipped entry: if buffer
     * is empty, will return -1
     * 
     * @return Length of skipped entry, if buffer was not empty; -1 if buffer
     *   was empty
     */
    public abstract int skipNextEntry();
    
    /**
     * Method that can be called to wait until there is at least one
     * entry available for reading.
     *<p>
     * Note that it is possible to have a race condition if there are
     * multiple readers, such that even if this method returns, following
     * read could fail/block (when another reader thread manages to thread
     * before current thread reads next entry); this method can only be
     * used to guarantee data for single-threaded reads, although it may
     * work as an optimization for multiple reader case as well.
     */
    public abstract void waitForNextEntry() throws InterruptedException;

    /**
     * Method that can be called to wait until there is at least one
     * entry available for reading; but to only wait up until specified
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
    public abstract void waitForNextEntry(long maxWaitMsecs) throws InterruptedException;
}
