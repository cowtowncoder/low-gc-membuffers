package com.fasterxml.util.membuf;

import java.util.*;

/*
 * Copyright Tatu Saloranta, 2011-
 */

/**
 * Container for a set of {@link MemBuffer}s. Used for constructing
 * {@link MemBuffer} instances that all share a single
 * {@link SegmentAllocator} instance, and global memory 
 * allocation limits (enforced by allocator).
 * 
 * @author Tatu Saloranta
 */
public class MemBuffers
{
    /**
     * Allocated used by 
     */
    protected final SegmentAllocator _segmentAllocator;

    /**
     * Queues that belong to this group
     */
    protected final ArrayList<MemBuffer> _queues;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    /**
     * Constructor that will create a default {@link SegmentAllocator}
     * instance with given arguments, and use that allocator for creating
     * {@link MemBuffer} instances.
     * 
     * @param segmentSize Size of segments allocated for buffers
     * @param segmentsToRetain Maximum number of segments allocator
     *   may reuse
     *   (see {@link SegmentAllocator} for details)
     * @param maxSegment Maximum number of allocated (and not released) segments
     *   allowed at any given point
     *   (see {@link SegmentAllocator} for details)
     */
    public MemBuffers(int segmentSize, int segmentsToRetain, int maxSegments)
    {
        this(new SegmentAllocator(segmentSize, segmentsToRetain, maxSegments));
    }

    /**
     * Constructor that will pass specified {@link SegmentAllocator}
     * for {@link MemBuffer} instances it creates.
     * 
     * @param allocator Allocator to use for instantiated {@link MemBuffer}s.
     */
    public MemBuffers(SegmentAllocator allocator) {
        _segmentAllocator = allocator;
        _queues = new ArrayList<MemBuffer>();
    }

    /*
    /**********************************************************************
    /* API
    /**********************************************************************
     */

    /**
     * Method that will try to create a {@link MemBuffer} with configured allocator,
     * using specified arguments.
     * If construction fails (due to allocation limits),
     * a {@link IllegalStateException} will be thrown.
     */
    public MemBuffer createBuffer(int minSegmentsForBuffer, int maxSegmentsForBuffer)
    {
        MemBuffer buf = tryCreateBuffer(minSegmentsForBuffer, maxSegmentsForBuffer);
        if (buf == null) {
            throw new IllegalStateException("Failed to create a MemBuffer due to segment allocation limits");
        }
        return buf;
    }

    /**
     * Method that will try to create a {@link MemBuffer} with configured allocator,
     * using specified arguments.
     * If construction fails (due to allocation limits),
     * null will be returned.
     */
    public MemBuffer tryCreateBuffer(int minSegmentsForBuffer, int maxSegmentsForBuffer)
            
    {
        Segment initialSegments = _segmentAllocator.allocateSegments(minSegmentsForBuffer, null);
        // may not be able to allocate segments; if so, need to fail
        if (initialSegments == null) {
            return null;
        }
        return new MemBuffer(_segmentAllocator, minSegmentsForBuffer, maxSegmentsForBuffer,
                initialSegments);
    }
}
