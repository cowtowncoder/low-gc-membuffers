package com.fasterxml.util.membuf;

import com.fasterxml.util.membuf.base.BytesSegment;
import com.fasterxml.util.membuf.base.MemBuffersBase;
import com.fasterxml.util.membuf.impl.ByteBufferBytesSegment;
import com.fasterxml.util.membuf.impl.ChunkyBytesMemBufferImpl;
import com.fasterxml.util.membuf.impl.StreamyBytesMemBufferImpl;

/**
 * Factory for creating {@link ChunkyBytesMemBuffer}s and {@link StreamyBytesMemBuffer}s,
 * memory buffers that contain byte sequences.
 *<p>
 * Default segments use {@link java.nio.ByteBuffer} for store byte sequences;
 * this can be overridden by specifying alternate
 * {@link SegmentAllocator} implementation.
 */
public class MemBuffersForBytes extends MemBuffersBase<
    BytesSegment,
    ChunkyBytesMemBuffer,
    StreamyBytesMemBuffer
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
    public MemBuffersForBytes(int segmentSize, int segmentsToRetain, int maxSegments)
    {
        this(ByteBufferBytesSegment.allocator(segmentSize, segmentsToRetain, maxSegments, true));
    }

    public MemBuffersForBytes(SegmentAllocator<BytesSegment> allocator) {
        this(allocator, null, null, null);
    }
    
    public MemBuffersForBytes(SegmentAllocator<BytesSegment> allocator,
            MemBufferDecorator<ChunkyBytesMemBuffer> chunkyDecorator,
            MemBufferDecorator<StreamyBytesMemBuffer> streamyDecorator,
            MemBufferTracker bufferTracker)
    {
        super(allocator, chunkyDecorator, streamyDecorator, bufferTracker);
    }
    
    public MemBuffersForBytes withAllocator(SegmentAllocator<BytesSegment> allocator) {
        return new MemBuffersForBytes(allocator, _chunkyDecorator, _streamyDecorator, _bufferTracker);
    }

    public MemBuffersForBytes withChunkyDecorator(MemBufferDecorator<ChunkyBytesMemBuffer> chunkyDecorator) {
        return new MemBuffersForBytes(_segmentAllocator, chunkyDecorator, _streamyDecorator, _bufferTracker);
    }

    public MemBuffersForBytes withStreamyDecorator(MemBufferDecorator<StreamyBytesMemBuffer> streamyDecorator) {
        return new MemBuffersForBytes(_segmentAllocator, _chunkyDecorator, streamyDecorator, _bufferTracker);
    }

    public MemBuffersForBytes withTracker(MemBufferTracker tracker) {
        return new MemBuffersForBytes(_segmentAllocator, _chunkyDecorator, _streamyDecorator, tracker);
    }
    
    /*
    /**********************************************************************
    /* Abstract method impls
    /**********************************************************************
     */

    @Override
    protected ChunkyBytesMemBuffer _createChunkyBuffer(int minSegmentsForBuffer, int maxSegmentsForBuffer,
            BytesSegment initialSegments, MemBufferTracker tracker)
    {
        return new ChunkyBytesMemBufferImpl(_segmentAllocator, minSegmentsForBuffer, maxSegmentsForBuffer,
                initialSegments, tracker);
        
    }

    @Override
    protected StreamyBytesMemBuffer _createStreamyBuffer(int minSegmentsForBuffer, int maxSegmentsForBuffer,
            BytesSegment initialSegments, MemBufferTracker tracker)
    {
        return new StreamyBytesMemBufferImpl(_segmentAllocator, minSegmentsForBuffer, maxSegmentsForBuffer,
                initialSegments, tracker);
    }
}
