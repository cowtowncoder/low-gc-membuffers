package com.fasterxml.util.membuf;

/*
 * Copyright Tatu Saloranta, 2011-
 */

/**
 * Core definition of containers for individual segments that form physical
 * storage level of the logical queue.
 * Note that at this level we don't yet know either physical primitive type
 * used under the hood (from byte to long) or underlying storage
 * method (array, {@link ByteBuffer}).  
 * All lengths are in units of fundamental primitive value
 * (i.e. single byte for byte-valued segments; units of 8 bytes
 * for long-valued and so on)
 *<p>
 * Type parameter is recursive due to having to return 'this' for
 * call chaining.
 * 
 * @author Tatu Saloranta
 */
public abstract class Segment<S extends Segment<S>>
{
    /**
     * Enumeration listing usual states a segment can be in.
     */
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
    /* API for accessors
    /**********************************************************************
     */

    /**
     * How many primitive values (of type segment contains) can still fit within this segment?
     */
    public abstract int availableForAppend();

    /**
     * How many primitive values (of type segment contains) could be read from this segment?
     */
    public abstract int availableForReading();
    
    /*
    /**********************************************************************
    /* Public API for state changes by MemBuffer
    /**********************************************************************
     */

    /**
     * Method called when the segment becomes the active write segment.
     *<p>
     * This state transition must occur from {@link State#FREE}.
     */
    public abstract S initForWriting();

    /**
     * Method called when writes to this segment have been completed,
     * which occurs when segment is full, more content is to be written,
     * and another segment is becoming the active write-segment.
     */
    public abstract S finishWriting();
    
    /**
     * Method called when the segment becomes the active read segment.
     * Its state may or may not change, but we do need to create the
     * reader-wrapper for ByteBuffer
     */
    public abstract S initForReading();

    /**
     * Method called when all contents has been read from this segment.
     * Will return next segment and clear it.
     * 
     * @return Next segment after this segment (before clearing link)
     */
    public abstract S finishReading();

    /**
     * Method that will erase any content segment may have and reset
     * various pointers: will be called when clearing buffer, the last
     * remaining segment needs to be cleared.
     */
    public abstract void clear();
    
    /*
    /**********************************************************************
    /* API for linkage handling
    /**********************************************************************
     */

    /**
     * Method for defining next segment in linked list
     */
    public abstract S relink(S next);

    /**
     * Accessor for getting the next segment in linked list.
     */
    public abstract S getNext();

    /*
    /**********************************************************************
    /* API for type-independent data access
    /**********************************************************************
     */

    /**
     * Method for trying to skip up to specified number of bytes.
     */
    public abstract int skip(int length);
}
