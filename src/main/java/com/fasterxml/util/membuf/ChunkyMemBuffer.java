package com.fasterxml.util.membuf;

public interface ChunkyMemBuffer
    extends MemBuffer
{
    /*
    /**********************************************************************
    /* Public API: type-independent data access
    /**********************************************************************
     */

    /**
     * Method for checking how many entries are buffered in this buffer
     * currently.
     */
    public abstract int getEntryCount();
    
    /**
     * Method that will check size of the next entry, if buffer has entries;
     * returns size in bytes if there is at least one entry, or -1 if buffer
     * is empty.
     * Note that this method does not remove the entry and can be called multiple
     * times, that is, it is fully idempotent.
     */
    public abstract int getNextEntryLength();
    
    /**
     * Method that will skip the next entry from the buffer, if an entry
     * is available, and return length of the skipped entry: if buffer
     * is empty, will return -1
     * 
     * @return Length of skipped entry, if buffer was not empty; -1 if buffer
     *   was empty
     */
    public abstract int skipNextEntry();

}
