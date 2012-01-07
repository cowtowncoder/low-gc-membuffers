package com.fasterxml.util.membuf;

import com.fasterxml.util.membuf.base.BytesSegment;
import com.fasterxml.util.membuf.base.MemBuffersBase;
import com.fasterxml.util.membuf.impl.ByteBufferBytesSegment;
import com.fasterxml.util.membuf.impl.ChunkyBytesMemBufferImpl;

/**
 * Factory for creating {@link ChunkyBytesMemBuffer}s, memory buffers that
 * contain byte sequences.
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

    public MemBuffersForBytes(SegmentAllocator<BytesSegment> allocator)
    {
        super(allocator);
    }
    
    /*
    /**********************************************************************
    /* Abstract method impls
    /**********************************************************************
     */

    @Override
    protected ChunkyBytesMemBuffer _createChunkyBuffer(int minSegmentsForBuffer, int maxSegmentsForBuffer,
            BytesSegment initialSegments)
    {
        return new ChunkyBytesMemBufferImpl(_segmentAllocator, minSegmentsForBuffer, maxSegmentsForBuffer,
                initialSegments);
        
    }

    @Override
    protected StreamyBytesMemBuffer _createStreamyBuffer(int minSegmentsForBuffer, int maxSegmentsForBuffer,
            BytesSegment initialSegments)
    {
        /*
        return new BytesMemBufferImpl(_segmentAllocator, minSegmentsForBuffer, maxSegmentsForBuffer,
                initialSegments);
                */
        return null;
    }
    
}
