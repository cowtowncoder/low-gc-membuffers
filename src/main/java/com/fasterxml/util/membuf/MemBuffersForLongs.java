package com.fasterxml.util.membuf;

import com.fasterxml.util.membuf.base.*;
import com.fasterxml.util.membuf.impl.ByteBufferLongsSegment;
import com.fasterxml.util.membuf.impl.ChunkyLongsMemBufferImpl;
import com.fasterxml.util.membuf.impl.StreamyLongsMemBufferImpl;

/**
 * Factory for creating {@link ChunkyBytesMemBuffer}s, memory buffers that
 * contain byte sequences.
 *<p>
 * Default segments use {@link java.nio.ByteBuffer} for store byte sequences;
 * this can be overridden by specifying alternate
 * {@link SegmentAllocator} implementation.
 */
public class MemBuffersForLongs extends MemBuffersBase<
    LongsSegment,
    ChunkyLongsMemBuffer,
    StreamyLongsMemBuffer
>
{
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
     * @param maxSegments Maximum number of allocated (and not released) segments
     *   allowed at any given point
     *   (see {@link SegmentAllocator} for details)
     */
    public MemBuffersForLongs(int segmentSize, int segmentsToRetain, int maxSegments)
    {
        this(ByteBufferLongsSegment.allocator(segmentSize, segmentsToRetain, maxSegments, true));
    }

    public MemBuffersForLongs(SegmentAllocator<LongsSegment> allocator) {
        super(allocator, null, null);
    }

    public MemBuffersForLongs(SegmentAllocator<LongsSegment> allocator,
            MemBufferDecorator<ChunkyLongsMemBuffer> chunkyDecorator,
            MemBufferDecorator<StreamyLongsMemBuffer> streamyDecorator)
    {
        super(allocator, chunkyDecorator, streamyDecorator);
    }
    
    public MemBuffersForLongs withAllocator(SegmentAllocator<LongsSegment> allocator) {
        return new MemBuffersForLongs(allocator, _chunkyDecorator, _streamyDecorator);
    }

    public MemBuffersForLongs withChunkyDecorator(MemBufferDecorator<ChunkyLongsMemBuffer> chunkyDecorator) {
        return new MemBuffersForLongs(_segmentAllocator, chunkyDecorator, _streamyDecorator);
    }

    public MemBuffersForLongs withStreamyDecorator(MemBufferDecorator<StreamyLongsMemBuffer> streamyDecorator) {
        return new MemBuffersForLongs(_segmentAllocator, _chunkyDecorator, streamyDecorator);
    }

    /*
    /**********************************************************************
    /* Abstract method impls
    /**********************************************************************
     */

    @Override
    protected ChunkyLongsMemBuffer _createChunkyBuffer(int minSegmentsForBuffer, int maxSegmentsForBuffer,
            LongsSegment initialSegments)
    {
        return new ChunkyLongsMemBufferImpl(_segmentAllocator, minSegmentsForBuffer, maxSegmentsForBuffer,
                initialSegments);
    }

    @Override
    protected StreamyLongsMemBuffer _createStreamyBuffer(int minSegmentsForBuffer, int maxSegmentsForBuffer,
            LongsSegment initialSegments)
    {
        return new StreamyLongsMemBufferImpl(_segmentAllocator, minSegmentsForBuffer, maxSegmentsForBuffer,
                initialSegments);
    }
}
