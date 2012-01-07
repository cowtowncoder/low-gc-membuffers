package com.fasterxml.util.membuf;

import com.fasterxml.util.membuf.base.BytesSegment;
import com.fasterxml.util.membuf.base.MemBuffersBase;
import com.fasterxml.util.membuf.impl.ByteBufferBytesSegmentAllocator;
import com.fasterxml.util.membuf.impl.BytesMemBufferImpl;

/**
 * Factory for creating {@link BytesMemBuffer}s, memory buffers that
 * contain byte sequences.
 *<p>
 * Default segments use {@link java.nio.ByteBuffer} for store byte sequences;
 * this can be overridden by specifying alternate
 * {@link SegmentAllocator} implementation.
 */
public class BytesMemBuffers extends MemBuffersBase<BytesMemBuffer, BytesSegment>
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
    public BytesMemBuffers(int segmentSize, int segmentsToRetain, int maxSegments)
    {
        this(new ByteBufferBytesSegmentAllocator(segmentSize, segmentsToRetain, maxSegments, true));
    }

    public BytesMemBuffers(SegmentAllocator<BytesSegment> allocator)
    {
        super(allocator);
    }
    
    /*
    /**********************************************************************
    /* Abstract method impls
    /**********************************************************************
     */

    @Override
    protected BytesMemBuffer _createBuffer(int minSegmentsForBuffer, int maxSegmentsForBuffer,
            BytesSegment initialSegments)
    {
        return new BytesMemBufferImpl(_segmentAllocator, minSegmentsForBuffer, maxSegmentsForBuffer,
                initialSegments);
        
    }

}
