package com.fasterxml.memq;

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
    
    public MemBuffers(int segmentSize, int minSegments, int maxSegments)
    {
        this(new SegmentAllocator(segmentSize, minSegments, maxSegments));
    }

    public MemBuffers(SegmentAllocator allocator) {
        _segmentAllocator = allocator;
        _queues = new ArrayList<MemBuffer>();
    }

    /*
    /**********************************************************************
    /* API
    /**********************************************************************
     */
}
