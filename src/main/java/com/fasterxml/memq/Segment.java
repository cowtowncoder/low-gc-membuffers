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
    
    /*
    /**********************************************************************
    /* Storage
    /**********************************************************************
     */
    
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
        _buffer = ByteBuffer.allocateDirect(size);
    }

    /**
     * Method called to indicate that the buffer is going to be reused,
     * meaning that any content currently stored can be discarded; and
     * that it should be added as head of segment chain indicated
     * by current head of the chain (which may be null)
     */
    public Segment resetForReuse(Segment head)
    {
        _buffer.clear();
        _nextSegment = head;
        return this;
    }
    
    /*
    /**********************************************************************
    /* Package methods, handling of linkage
    /**********************************************************************
     */

    public Segment relink(Segment next)
    {
        _nextSegment = next;
        return this;
    }

    public Segment getNext() {
        return _nextSegment;
    }

    /*
    /**********************************************************************
    /* Package methods, properties
    /**********************************************************************
     */

    /**
     * How many bytes can still fit within this segment?
     */
    public int available() {
        return _buffer.remaining();
    }

    /*
    /**********************************************************************
    /* Package methods, appending data
    /**********************************************************************
     */
    
    /**
     * Append operation that appends specified data; caller must ensure
     * that it will actually fit (if it can't, it should instead call
     * {@link #tryAppend}).
     */
    public void append(byte[] src, int offset, int length)
    {
        _buffer.put(src, offset, length);
    }

    /**
     * Append operation that tries to append as much of input data as
     * possible, and returns number of bytes that were copied
     */
    public int tryAppend(byte[] src, int offset, int length)
    {
        int actualLen = Math.min(length, available());
        if (actualLen > 0) {
            _buffer.put(src, offset, actualLen);
        }
        return actualLen;
    }
}
