package com.fasterxml.util.membuf.impl;

import java.nio.ByteBuffer;

import com.fasterxml.util.membuf.*;

/**
 * {@link Segment} implementation that uses {@link ByteBuffer}s for
 * storing data.
 * Basically a wrapper around (direct) {@link ByteBuffer},
 * adding state information and linkage to next segment in chain
 * (of used or free segments).
 * 
 * @author Tatu Saloranta
 */
public class ByteBufferSegment extends Segment
{
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
    /* API: state changes
    /**********************************************************************
     */
    
    public ByteBufferSegment(int size, boolean useDirect)
    {
        if (size < ABSOLUTE_MINIMUM_LENGTH) {
            size = ABSOLUTE_MINIMUM_LENGTH;
        }
        _buffer = useDirect ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size);
        _state = State.FREE;
    }

    @Override
    public Segment initForWriting()
    {
        if (_state != State.FREE) {
            throw new IllegalStateException("Trying to initForWriting segment, state "+_state);
        }
        _state = State.WRITING;
        return this;
    }

    @Override
    public Segment finishWriting()
    {
        if (_state != State.WRITING && _state != State.READING_AND_WRITING) {
            throw new IllegalStateException("Trying to finishWriting segment, state "+_state);
        }
        _state = State.READING;
        // Let's not yet create wrapper buffer for reading until it is actually needed
        return this;
    }
    
    @Override
    public Segment initForReading()
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

    @Override
    public Segment finishReading()
    {
        if (_state != State.READING) {
            throw new IllegalStateException("Trying to finishReading, state "+_state);
        }
        _state = State.FREE;
        Segment result = _nextSegment;
        relink(null);
        // clear write pointer for further reuse
        _buffer.clear();
        // and drop reference to read-wrapper:
        _readBuffer = null;
        return result;
    }

    /**
     * Method that will erase any content segment may have and reset
     * various pointers: will be called when clearing buffer, the last
     * remaining segment needs to be cleared.
     */
    @Override
    public void clear()
    {
        // temporarily change state to 'free'
        _state = State.FREE;
        _readBuffer = null; // could/should we just reset it instead?
        _buffer.clear(); // clear the write pointer
        // so that we can do same call sequence as when instances are created
        initForWriting();
        initForReading();
        // so that state should now be READING_AND_WRITING
    }
    
    /*
    /**********************************************************************
    /* API: linkage
    /**********************************************************************
     */

    @Override
    public Segment relink(Segment next)
    {
        // sanity check; should be possible to remove in future
        if (next == this) {
            throw new IllegalStateException("trying to set cyclic link");
        }
        _nextSegment = next;
        return this;
    }

    @Override
    public Segment getNext() {
        return _nextSegment;
    }

    /*
    /**********************************************************************
    /* Package methods, properties
    /**********************************************************************
     */

    @Override
    public int availableForAppend() {
        return _buffer.remaining();
    }

    @Override
    public int availableForReading() {
        if (_readBuffer == null) { // sanity check...
            throw new IllegalStateException("Method should not be called when _readBuffer is null");
        }
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
    @Override
    public void append(byte[] src, int offset, int length) {
        _buffer.put(src, offset, length);
    }

    /**
     * Append operation that tries to append as much of input data as
     * possible, and returns number of bytes that were copied
     * 
     * @return Number of bytes actually appended
     */
    @Override
    public int tryAppend(byte[] src, int offset, int length)
    {
        int actualLen = Math.min(length, availableForAppend());
        if (actualLen > 0) {
            _buffer.put(src, offset, actualLen);
        }
        return actualLen;
    }

    /*
    /**********************************************************************
    /* Reading data
    /**********************************************************************
     */

    @Override
    public void read(byte[] buffer, int offset, int length)
    {
        _readBuffer.get(buffer, offset, length);
    }

    /**
     * @return Number of bytes actually read
     */
    @Override
    public int tryRead(byte[] buffer, int offset, int length)
    {
        int actualLen = Math.min(availableForReading(), length);
        if (actualLen > 0) {
            _readBuffer.get(buffer, offset, actualLen);
        }
        return actualLen;
    }
    
    /**
     * Method for reading as much of the length prefix as possible from
     * the current pointer in this segment. This will be from 0 to 5 bytes,
     * depending on length of prefix, and whether it resides completely in
     * this segment or not.
     */
    @Override
    public int readLength()
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
    @Override
    public int readSplitLength(int partial)
    {
        while (true) {
            partial = (partial << 7);
            int b = _readBuffer.get();
            if (b < 0) { // complete...
                return partial + (b & 0x7F);
            }
            partial += b;
        }
    }
}
