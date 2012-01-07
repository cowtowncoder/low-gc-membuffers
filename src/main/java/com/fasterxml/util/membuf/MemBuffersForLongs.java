package com.fasterxml.util.membuf;

import com.fasterxml.util.membuf.base.*;
import com.fasterxml.util.membuf.impl.ByteBufferLongsSegmentAllocator;
import com.fasterxml.util.membuf.impl.LongsMemBufferImpl;

/**
 * Factory for creating {@link BytesMemBuffer}s, memory buffers that
 * contain byte sequences.
 *<p>
 * Default segments use {@link java.nio.ByteBuffer} for store byte sequences;
 * this can be overridden by specifying alternate
 * {@link SegmentAllocator} implementation.
 */
public class MemBuffersForLongs extends MemBuffersBase<LongsMemBuffer, LongsSegment>
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
        this(new ByteBufferLongsSegmentAllocator(segmentSize, segmentsToRetain, maxSegments, true));
    }

    public MemBuffersForLongs(SegmentAllocator<LongsSegment> allocator)
    {
        super(allocator);
    }
    
    /*
    /**********************************************************************
    /* Abstract method impls
    /**********************************************************************
     */

    @Override
    protected LongsMemBuffer _createChunkedBuffer(int minSegmentsForBuffer, int maxSegmentsForBuffer,
            LongsSegment initialSegments)
    {
        return new LongsMemBufferImpl(_segmentAllocator, minSegmentsForBuffer, maxSegmentsForBuffer,
                initialSegments);
        
    }

}
