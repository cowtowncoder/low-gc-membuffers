package com.fasterxml.util.membuf.base;

import com.fasterxml.util.membuf.Segment;

/**
 * Shared base class for {@link Segment} implementations
 */
public abstract class SegmentBase<S extends Segment<S>>
    extends Segment<S>
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
    protected S _nextSegment;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */
    
    public SegmentBase()
    {
        _state = State.FREE;        
    }
    
    /*
    /**********************************************************************
    /* Partial API implementation: state changes
    /**********************************************************************
     */

    @Override
    public S initForWriting()
    {
        if (_state != State.FREE) {
            throw new IllegalStateException("Trying to initForWriting segment, state "+_state);
        }
        _state = State.WRITING;
        return _this();
    }

    @Override
    public S finishWriting()
    {
        if (_state != State.WRITING && _state != State.READING_AND_WRITING) {
            throw new IllegalStateException("Trying to finishWriting segment, state "+_state);
        }
        _state = State.READING;
        // Let's not yet create wrapper buffer for reading until it is actually needed
        return _this();
    }

    @Override
    public S initForReading()
    {
        if (_state == State.WRITING) {
            _state = State.READING_AND_WRITING;
        } else if (_state == State.READING) { // writing already completed
            ; // state is fine as is
        } else {
            throw new IllegalStateException("Trying to initForReading segment, state "+_state);
        }
        return _this();
    }

    @Override
    public S finishReading()
    {
        if (_state != State.READING) {
            throw new IllegalStateException("Trying to finishReading, state "+_state);
        }
        _state = State.FREE;
        S result = _nextSegment;
        relink(null);
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
        // so that we can do same call sequence as when instances are created
        initForWriting();
        initForReading();
        // so that state should now be READING_AND_WRITING
    }
    
    /*
    /**********************************************************************
    /* Partial API implementation: linkage
    /**********************************************************************
     */

    @Override
    public S relink(S next)
    {
        // sanity check; should be possible to remove in future
        if (next == this) {
            throw new IllegalStateException("trying to set cyclic link");
        }
        _nextSegment = next;
        return _this();
    }

    @Override
    public S getNext() {
        return _nextSegment;
    }

    /*
    /**********************************************************************
    /* Partial API implementation: properties
    /**********************************************************************
     */

    // public int availableForAppend();

    // public int availableForReading();

    /*
    /**********************************************************************
    /* Partial API implementation: appending data
    /**********************************************************************
     */
    
    //public void append(byte[] src, int offset, int length);

    //public int tryAppend(byte[] src, int offset, int length)
    
    // Silly work-around for generics problem:
    @SuppressWarnings("unchecked")
    private final S _this() {
        return (S) this;
    }
}

