package com.fasterxml.memq;

/**
 * Copyright Tatu Saloranta, 2011-
 * 
 * Actual memory queue implementation, which uses set of {@link MemQSegment}s as
 * virtual ring buffer. Number of segments used is bound by minimum and maximum
 * amounts, which defines minimim and maximum memory usage.
 * Memory usage is relatively easy to estimate since data is stored as
 * byte sequences and almost all memory is simply used by
 * allocated <code>ByteBuffer</code>s.
 *<p>
 * Access to queue is fully synchronized, since parts will have to be anyway
 * (updating of stats, pointers), and since all real-world use cases will
 * need some level of synchronization anyway, even with just single producer
 * and consumer. If it turns out that there are bottlenecks that could be
 * avoided with more granular (or external) locking, this design can be
 * revisited.
 * 
 * @author Tatu Saloranta
 */
public class MemQ
{
    /*
    /**********************************************************************
    /* Basic configuration
    /**********************************************************************
     */

    /**
     * Smallest number of segments to allocate.
     * This defines the smallest
     * physical size of the queue, such that queue will never shrink beyond
     * this setting.
     * Lowest allowed minimum size is 2, since head and tail of the queue
     * must reside on different segments (to allow expansion)
     */
    protected final int _minSegments;

    /**
     * Maximum number of segments to allocate.
     * This defines maximum physical size of the queue.
     */
    protected final int _maxSegments;

    /**
     * Size of allocated segments in bytes.
     */
    protected final int _segmentSize;
    
    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    public MemQ(int segmentSize, int minSegments, int maxSegments)
    {
        _segmentSize = segmentSize;
        _minSegments = minSegments;
        _maxSegments = maxSegments;
    }

    /*
    /**********************************************************************
    /* API
    /**********************************************************************
     */

    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */
    
}
