package com.fasterxml.util.membuf.base;

import com.fasterxml.util.membuf.*;

/**
 * Intermediate base class for {@link SegmentAllocator}s
 * that construct {@link Segment}s that store byte sequences.
 */
public abstract class BytesSegmentAllocator
    extends SegmentAllocator<BytesSegment>
{
    /**
     * As a sanity check, we will not allow segments shorter than 4 bytes;
     * even that small makes little sense, but is useful for unit tests.
     */
    public final static int MIN_SEGMENT_LENGTH = 4;
    
    /*
    /**********************************************************************
    /* State
    /**********************************************************************
     */
    
    /**
     * And here we hold on to segments that have been returned by buffers
     * after use.
     */
    protected BytesSegment _firstReusableSegment;
    
    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    /**
     * Constructor for creating simple allocator instances.
     * Basic configuration is used to limit amount of memory that will
     * be used (by {@link maxSegments}), as well as degree to which
     * allocator can reuse released segments (to reduce churn by freeing
     * and reallocating segments, as segments typically use native
     * {@link java.nio.ByteBuffer}s that may be costly to allocate.
     * 
     * @param segmentSize Length (in bytes) of segments to allocate
     * @param minSegmentsToRetain Number of segments that we will retain after
     *   being released, for reuse; if 0, will not reuse any segments (although
     *   individual buffers may still do local reuse)
     * @param maxSegments Maximum number of allocated (and not released) segments
     *   allowed at any given point;
     *   strictly limits maximum memory usage by all {@link MemBuffer}s that
     *   use this allocator.
     */
    public BytesSegmentAllocator(int segmentSize, int minSegmentsToRetain, int maxSegments)
    {
        super(segmentSize, minSegmentsToRetain, maxSegments);
        if (segmentSize < MIN_SEGMENT_LENGTH) {
            throw new IllegalArgumentException("segmentSize minimum is "+MIN_SEGMENT_LENGTH+" bytes");
        }
        _firstReusableSegment = null;
    }

    /*
    /**********************************************************************
    /* Extended byte-specific API
    /**********************************************************************
     */
    
    /**
     * Method that will try to allocate specified number of segments
     * (and exactly that number; no less).
     * If this can be done, segments are allocated and prepended into
     * given segment list; otherwise nothing is allocated and
     * null is returned.
     * 
     * @param count Number of segments to allocate
     *   
     * @return Head of segment list (with newly allocated entries as first
     *    entries) if allocation succeeded; null if not
     */
    public synchronized BytesSegment allocateSegments(int count, BytesSegment segmentList)
    {
        if (count < 1) {
            throw new IllegalArgumentException("Must allocate at least one segment (count = "+count+")");
        }
        if (!_canAllocate(count)) {
            return null;
        }
        for (int i = 0; i < count; ++i) {
            segmentList = _allocateSegment().relink(segmentList);
        }
        return segmentList;
    }
    
    /**
     * Method called by {@link MemBuffer} instances when they have consumed
     * contents of a segment and do not want to hold on to it for local
     * reuse.
     */
    public synchronized void releaseSegment(BytesSegment segToRelease)
    {
        if (--_bufferOwnedSegmentCount < 0) { // sanity check; not needed in perfect world
            int count = _bufferOwnedSegmentCount;
            _bufferOwnedSegmentCount = 0; // "fix"
            throw new IllegalStateException("Bugger! Corruption Maximus: _bufferOwnedSegmentCount went below 0 ("
                    +count+")");
        }
        // Can we reuse it?
        if (_reusableSegmentCount < _maxReusableSegments) {
            _firstReusableSegment = segToRelease.relink(_firstReusableSegment);
            ++_reusableSegmentCount;
        }
    }

    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */

    protected abstract BytesSegment _allocateSegment();
    
    protected boolean _canAllocate(int count)
    {
        int available = _reusableSegmentCount + (_maxSegmentsToAllocate - _bufferOwnedSegmentCount);
        return (available >= count);
    }
}
