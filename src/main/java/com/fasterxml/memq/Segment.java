package com.fasterxml.memq;

import java.nio.ByteBuffer;

/*
 * Copyright Tatu Saloranta, 2011-
 */

/**
 * Container for individual segments that form physical storage level of
 * the logical queue.
 * 
 * @author Tatu Saloranta
 */
public class Segment
{
    /*
    /**********************************************************************
    /* Linking
    /**********************************************************************
     */

    /**
     * Next segment in the segment chain
     */
    protected Segment _nextSegment;

    /**
     * Number of segments reachable via {@link #_nextSegment}
     */
    protected int _tailCount;
    
    /*
    /**********************************************************************
    /* Storage
    /**********************************************************************
     */

    /**
     * Size of the underlying buffer in bytes
     */
    protected final int _bufferSize;
    
    /**
     * Underlying low-level buffer in which raw entry data is queued
     */
    protected final ByteBuffer _buffer;

    /**
     * Pointer within buffer to the first byte available to be read;
     * has to be less than or equal to <code>_writeOffset</code>.
     */
    protected int _readOffset;

    /**
     * Pointer within buffer to location where new data can be appended;
     * less than equal to <code>_bufferSize</code>.
     */
    protected int _writeOffset;
    
    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    public Segment(int size)
    {
        _bufferSize = size;
        _buffer = ByteBuffer.allocateDirect(size);
    }
    
    /*
    /**********************************************************************
    /* API
    /**********************************************************************
     */
    
}
