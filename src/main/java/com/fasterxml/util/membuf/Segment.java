package com.fasterxml.util.membuf;

/*
 * Copyright Tatu Saloranta, 2011-
 */

/**
 * Core definition of containers for individual segments that form physical
 * storage level of the logical queue.
 * Note that at this level we don't yet know physical primitive type
 * used under the hood (from byte to long): all lengths are in these
 * fundamental units (i.e. single byte for byte type; units of 8 bytes
 * for longs and so on)
 * 
 * @author Tatu Saloranta
 */
public abstract class Segment
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

    /**
     * Let's not allow using segments shorter than 8 bytes; partly
     * to ensure that length prefixes can not be split across more
     * than one segments.
     */
    public final static int ABSOLUTE_MINIMUM_LENGTH = 8;    

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
    public abstract Segment initForWriting();

    /**
     * Method called when writes to this segment have been completed,
     * which occurs when segment is full, more content is to be written,
     * and another segment is becoming the active write-segment.
     */
    public abstract Segment finishWriting();
    
    /**
     * Method called when the segment becomes the active read segment.
     * Its state may or may not change, but we do need to create the
     * reader-wrapper for ByteBuffer
     */
    public abstract Segment initForReading();

    /**
     * Method called when all contents has been read from this segment.
     * Will return next segment and clear it.
     * 
     * @return Next segment after this segment (before clearing link)
     */
    public abstract Segment finishReading();

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
    public abstract Segment relink(Segment next);

    /**
     * Accessor for getting the next segment in linked list.
     */
    public abstract Segment getNext();
    /*
    /**********************************************************************
    /* API: appending data
    /**********************************************************************
     */
    
    /**
     * Append operation that appends specified data; caller must ensure
     * that it will actually fit (if it can't, it should instead call
     * {@link #tryAppend}).
     */
    public abstract void append(byte[] src, int offset, int length);

    /**
     * Append operation that tries to append as much of input data as
     * possible, and returns number of bytes that were copied
     * 
     * @return Number of bytes actually appended
     */
    public abstract int tryAppend(byte[] src, int offset, int length);

    /*
    /**********************************************************************
    /* API: reading data
    /**********************************************************************
     */

    public abstract int readLength();

    public abstract int readSplitLength(int partial);

    public abstract void read(byte[] buffer, int offset, int length);

    public abstract int tryRead(byte[] buffer, int offset, int length);

    public abstract int skip(int length);
}
