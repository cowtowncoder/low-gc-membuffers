package com.fasterxml.util.membuf.base;

import com.fasterxml.util.membuf.ChunkyMemBuffer;
import com.fasterxml.util.membuf.MemBuffer;
import com.fasterxml.util.membuf.Segment;
import com.fasterxml.util.membuf.SegmentAllocator;
import com.fasterxml.util.membuf.StreamyMemBuffer;

/*
 * Copyright Tatu Saloranta, 2011-
 */

/**
 * Container for a set of {@link MemBuffer}s. Used for constructing
 * {@link MemBuffer} instances that all share a single
 * {@link SegmentAllocator} instance, and global memory 
 * allocation limits (enforced by allocator).
 *<p>
 * Note that sub-classing is explicitly supported; this is needed to
 * use custom {@link SegmentAllocator}s, {@link Segment}s and/or {@link MemBuffer}s.
 * Default {@link MemBuffersBase} implementation will use default implementations
 * of other components.
 *<p>
 * Also note that while this object is the factory for {@link MemBuffer} instances,
 * it does not keep references to instances created. Because of this, one
 * has to explicitly close actual buffer instances.
 * 
 * @author Tatu Saloranta
 */
public abstract class MemBuffersBase<S extends Segment<S>,
    CB extends ChunkyMemBuffer,
    SB extends StreamyMemBuffer
>
{
    /**
     * Allocator used by buffers constructed by this object.
     */
    protected final SegmentAllocator<S> _segmentAllocator;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    /**
     * Constructor that will pass specified {@link SegmentAllocator}
     * for {@link MemBuffer} instances it creates.
     * 
     * @param allocator Allocator to use for instantiated {@link MemBuffer}s.
     */
    public MemBuffersBase(SegmentAllocator<S> allocator) {
        _segmentAllocator = allocator;
    }

    /*
    /**********************************************************************
    /* API: config access
    /**********************************************************************
     */

    public final SegmentAllocator<S> getAllocator() { return _segmentAllocator; }

    /*
    /**********************************************************************
    /* API: factory methods for "chunky" mem buffers
    /**********************************************************************
     */
    
    /**
     * Method that will try to create a {@link ChunkyMemBuffer} with configured allocator,
     * using specified arguments.
     * If construction fails (due to allocation limits),
     * a {@link IllegalStateException} will be thrown.
     */
    public final CB createChunkyBuffer(int minSegmentsForBuffer, int maxSegmentsForBuffer)
    {
        CB buf = tryCreateChunkyBuffer(minSegmentsForBuffer, maxSegmentsForBuffer);
        if (buf == null) {
            throw new IllegalStateException("Failed to create a MemBuffer due to segment allocation limits");
        }
        return buf;
    }

    /**
     * Method that will try to create a {@link ChunkyMemBuffer} with configured allocator,
     * using specified arguments.
     * If construction fails (due to allocation limits),
     * null will be returned.
     */
    public final CB tryCreateChunkyBuffer(int minSegmentsForBuffer, int maxSegmentsForBuffer)
    {
        S initialSegments = _segmentAllocator.allocateSegments(minSegmentsForBuffer, null);
        // may not be able to allocate segments; if so, need to fail
        if (initialSegments == null) {
            return null;
        }
        return _createChunkyBuffer(minSegmentsForBuffer, maxSegmentsForBuffer, initialSegments);
    }

    /*
    /**********************************************************************
    /* API: factory methods for "streamy" mem buffers
    /**********************************************************************
     */
    
    /**
     * Method that will try to create a {@link StreamyMemBuffer} with configured allocator,
     * using specified arguments.
     * If construction fails (due to allocation limits),
     * a {@link IllegalStateException} will be thrown.
     */
    public final SB createStreamyBuffer(int minSegmentsForBuffer, int maxSegmentsForBuffer)
    {
        SB buf = tryCreateStreamyBuffer(minSegmentsForBuffer, maxSegmentsForBuffer);
        if (buf == null) {
            throw new IllegalStateException("Failed to create a MemBuffer due to segment allocation limits");
        }
        return buf;
    }

    /**
     * Method that will try to create a {@link StreamyMemBuffer} with configured allocator,
     * using specified arguments.
     * If construction fails (due to allocation limits),
     * null will be returned.
     */
    public final SB tryCreateStreamyBuffer(int minSegmentsForBuffer, int maxSegmentsForBuffer)
    {
        S initialSegments = _segmentAllocator.allocateSegments(minSegmentsForBuffer, null);
        // may not be able to allocate segments; if so, need to fail
        if (initialSegments == null) {
            return null;
        }
        return _createStreamyBuffer(minSegmentsForBuffer, maxSegmentsForBuffer, initialSegments);
    }
    
    /*
    /**********************************************************************
    /* Abstract methods for sub-classes
    /**********************************************************************
     */

    /**
     * Internal factory method for creating type-specific "chunky" mem buffer instance
     */
    protected abstract CB _createChunkyBuffer(int minSegmentsForBuffer, int maxSegmentsForBuffer,
            S initialSegments);

    /**
     * Internal factory method for creating type-specific "streamy" mem buffer instance
     */
    protected abstract SB _createStreamyBuffer(int minSegmentsForBuffer, int maxSegmentsForBuffer,
            S initialSegments);
}
