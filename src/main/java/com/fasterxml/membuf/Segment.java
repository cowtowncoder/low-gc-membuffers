package com.fasterxml.membuf;

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
    
    /**
     * Let's not allow using segments shorter than 16 bytes; partly
     * to ensure that length prefixes can not be split across more
     * than one segments.
     */
    public final static int ABSOLUTE_MINIMUM_LENGTH = 16;
    
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
        if (size < ABSOLUTE_MINIMUM_LENGTH) {
            size = ABSOLUTE_MINIMUM_LENGTH;
        }
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
     * and read position has moved to a new segment.
     */
    protected Segment finishReading()
    {
        if (_state != State.READING) {
            throw new IllegalStateException("Trying to finishReading, state "+_state);
        }
        _state = State.FREE;
        _nextSegment = null;
        // clear write pointer for further reuse
        _buffer.clear();
        // and drop reference to read-wrapper:
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

    public int availableForReading() {
        /*
        if (_readBuffer == null) { // not yet initialized for reading? how much have we written?
            return _buffer.position();
        }
        */
        return _readBuffer.remaining();
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
    protected void append(byte[] src, int offset, int length)
    {
        _buffer.put(src, offset, length);
    }

    /**
     * Append operation that tries to append as much of input data as
     * possible, and returns number of bytes that were copied
     * 
     * @return Number of bytes actually appended
     */
    protected int tryAppend(byte[] src, int offset, int length)
    {
        int actualLen = Math.min(length, availableForAppend());
        if (actualLen > 0) {
            _buffer.put(src, offset, actualLen);
        }
        return actualLen;
    }

    /*
    /**********************************************************************
    /* Package methods, reading data
    /**********************************************************************
     */

    /**
     * Method for reading as much of the length prefix as possible from
     * the current pointer in this segment. This will be from 0 to 5 bytes,
     * depending on length of prefix, and whether it resides completely in
     * this segment or not.
     */
    protected int readLength()
    {
        int available = availableForReading();
        if (available == 0) {
            return -1;
        }
        int length = _readBuffer.get();

        if (length < 0) { // single-byte length, simple
            return (length & 0x7F);
        }
        if (--available == 0) {
            return -(length + 1);
        }

        // second byte:
        int b = _readBuffer.get();
        if (b < 0) { // two-byte completed
            return (length << 7) + (b & 0x7F);
        }
        length = (length << 7) + b;
        if (--available == 0) {
            return -(length + 1);
        }

        // third byte:
        b = _readBuffer.get();
        if (b < 0) {
            return (length << 7) + (b & 0x7F);
        }
        length = (length << 7) + b;
        if (--available == 0) {
            return -(length + 1);
        }

        // fourth byte:
        b = _readBuffer.get();
        if (b < 0) {
            return (length << 7) + (b & 0x7F);
        }
        length = (length << 7) + b;
        if (--available == 0) {
            return -(length + 1);
        }

        // fifth and last byte
        b = _readBuffer.get();
        if (b < 0) {
            return (length << 7) + (b & 0x7F);
        }
        // ... or, corrupt data
        throw new IllegalStateException("Corrupt segment: fifth byte of length was 0x"+Integer.toHexString(b)+" did not have high-bit set");
    }

    /**
     * Method that is called to read length prefix that is split across
     * segment boundary. Due to
     */
    protected int readSplitLength(int partial)
    {
        while (true) {
            partial = (partial << 7);
            int b = _buffer.get();
            if (b < 0) { // complete...
                return partial + (b & 0x7F);
            }
            partial += b;
        }
    }

    protected void read(byte[] buffer, int offset, int length)
    {
        _readBuffer.get(buffer, offset, length);
    }

    /**
     * 
     * @return Number of bytes actually read
     */
    protected int tryRead(byte[] buffer, int offset, int length)
    {
        int actualLen = Math.min(availableForReading(), length);
        if (actualLen > 0) {
            _readBuffer.get(buffer, offset, length);
        }
        return actualLen;
    }
}
