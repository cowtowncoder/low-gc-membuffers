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
    protected enum State {
        /**
         * Initial state, as well as state when stored in various free segment
         * chains.
         */
        FREE,
    
        /**
         * State when content is being appended and segment is the currently
         * active write segment for a buffer instance, but it is not yet
         * being read from.
         */
        WRITING,

        /**
         * State when content is both still being written, and already being
         * read.
         */
        READING_AND_WRITING,
        
        /**
         * State after segment has been completely written but is still being
         * read from.
         */
        READING
        ;
    }
    
    /*
    /**********************************************************************
    /* State
    /**********************************************************************
     */

    protected State _state;

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
     * Wrapper buffer used for reading previously written content; wrapper
     * used to allow separate pointers for reading and writing content.
     */
    protected ByteBuffer _readBuffer;
    
    /*
    /**********************************************************************
    /* Life-cycle, including state changes
    /**********************************************************************
     */

    public Segment(int size)
    {
        _buffer = ByteBuffer.allocateDirect(size);
        _state = State.FREE;
    }

    /**
     * Method called when the segment becomes the active write segment.
     *<p>
     * This state transition must occur from {@link State#FREE}.
     */
    protected Segment initForWriting(Segment prevWriteSegment)
    {
        if (_state != State.FREE) {
            throw new IllegalStateException("Trying to initForWriting segment, state "+_state);
        }
        _state = State.WRITING;
        _nextSegment = prevWriteSegment;
        return this;
    }

    /**
     * Method called when writes to this segment have been completed,
     * which occurs when segment is full, more content is to be written,
     * and another segment is becoming the active write-segment.
     */
    protected Segment finishWriting()
    {
        if (_state != State.WRITING && _state != State.READING_AND_WRITING) {
            throw new IllegalStateException("Trying to finishWriting segment, state "+_state);
        }
        _state = State.READING;
        // Let's not yet create wrapper buffer for reading until it is actually needed
        return this;
    }
    
    /**
     * Method called when the segment becomes the active read segment.
     * Its state may or may not change, but we do need to create the
     * reader-wrapper for ByteBuffer
     */
    protected Segment initForReading()
    {
        if (_state == State.WRITING) {
            _state = State.READING_AND_WRITING;
        } else if (_state == State.READING) { // writing already completed
            ; // state is fine as is
        } else {
            throw new IllegalStateException("Trying to initForReading segment, state "+_state);
        }
        _readBuffer = _buffer.asReadOnlyBuffer();
        _readBuffer.clear();
        return this;
    }

    /**
     * Method called when all contents has been read from this segment,
     * and read position moves to a new segment.
     */
    protected Segment finishReading(Segment freeSegmentChain)
    {
        if (_state != State.READING) {
            throw new IllegalStateException("Trying to finishReading, state "+_state);
        }
        _state = State.FREE;
        _nextSegment = freeSegmentChain;
        // clear write pointer for further reuse
        _buffer.clear();
        _readBuffer = null;
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
    public int availableForAppend() {
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
        int actualLen = Math.min(length, availableForAppend());
        if (actualLen > 0) {
            _buffer.put(src, offset, actualLen);
        }
        return actualLen;
    }
}
