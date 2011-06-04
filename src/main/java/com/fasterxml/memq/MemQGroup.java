package com.fasterxml.memq;

import java.util.*;

/*
 * Copyright Tatu Saloranta, 2011-
 */

/**
 * Container for set of {@link MemQ}s. Used for constructing
 * {@link MemQ} instances, which all share a single
 * {@link SegmentAllocator} instance, and global memory 
 * allocation limits (enforced by allocator).
 * 
 * @author Tatu Saloranta
 */
public class MemQGroup
{
    /**
     * Allocated used by 
     */
    protected final SegmentAllocator _segmentAllocator;

    /**
     * Queues that belong to this group
     */
    protected final ArrayList<MemQ> _queues;
    
    public MemQGroup(int segmentSize, int minSegments, int maxSegments)
    {
        this(new SegmentAllocator(segmentSize, minSegments, maxSegments));
    }

    public MemQGroup(SegmentAllocator allocator) {
        _segmentAllocator = allocator;
        _queues = new ArrayList<MemQ>();
    }
}
