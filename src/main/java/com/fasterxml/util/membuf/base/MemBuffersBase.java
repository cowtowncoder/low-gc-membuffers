package com.fasterxml.util.membuf.base;

import com.fasterxml.util.membuf.ChunkyMemBuffer;
import com.fasterxml.util.membuf.MemBuffer;
import com.fasterxml.util.membuf.MemBufferDecorator;
import com.fasterxml.util.membuf.MemBufferTracker;
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
 * it does not keep references to instances created. Because of this, it is
 * important that buffers are closed (either explicitly, or by using appropriate
 * wrappers to do that).
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

    /**
     * Optional object for decorating "chunky" {@link MemBuffer}
     * instances that factory creates.
     */
    protected final MemBufferDecorator<CB> _chunkyDecorator;

    /**
     * Optional object for decorating "streamy" {@link MemBuffer}
     * instances that factory creates.
     */
    protected final MemBufferDecorator<SB> _streamyDecorator;

    /**
     * Optional manager object that can be registered to add support
     * for features like auto-closing.
     * 
     * @since 0.9.2
     */
    protected final MemBufferTracker _bufferTracker;

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
        this(allocator, null, null, null);
    }

    public MemBuffersBase(SegmentAllocator<S> allocator,
            MemBufferDecorator<CB> chunkyDecorator,
            MemBufferDecorator<SB> streamyDecorator,
            MemBufferTracker bufferTracker)
    {
        _segmentAllocator = allocator;
        _chunkyDecorator = chunkyDecorator;
        _streamyDecorator = streamyDecorator;
        _bufferTracker = bufferTracker;
    }
    
    /*
    /**********************************************************************
    /* API: config access
    /**********************************************************************
     */

    public final SegmentAllocator<S> getAllocator() { return _segmentAllocator; }

    public final MemBufferDecorator<CB> getChunkyDecorator() { return _chunkyDecorator; }
    public final MemBufferDecorator<SB> getStreamyDecorator() { return _streamyDecorator; }

    public final MemBufferTracker getBufferTracker() { return _bufferTracker; }

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
        CB buffer = _createChunkyBuffer(minSegmentsForBuffer,
                maxSegmentsForBuffer, initialSegments, _bufferTracker);
        // Need to decorate it?
        if (_chunkyDecorator != null) {
            buffer = _chunkyDecorator.decorateMemBuffer(buffer);
        }
        return buffer;
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
        SB buffer = _createStreamyBuffer(minSegmentsForBuffer,
                maxSegmentsForBuffer, initialSegments, _bufferTracker);
        if (_streamyDecorator != null) {
            buffer = _streamyDecorator.decorateMemBuffer(buffer);
        }
        return buffer;
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
            S initialSegments,
            MemBufferTracker tracker);

    /**
     * Internal factory method for creating type-specific "streamy" mem buffer instance
     */
    protected abstract SB _createStreamyBuffer(int minSegmentsForBuffer, int maxSegmentsForBuffer,
            S initialSegments,
            MemBufferTracker tracker);
}
