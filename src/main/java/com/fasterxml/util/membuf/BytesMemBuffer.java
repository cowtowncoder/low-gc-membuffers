package com.fasterxml.util.membuf;

public abstract class BytesMemBuffer extends MemBuffer
{
    public BytesMemBuffer() {
        
    }

    /*
    /**********************************************************************
    /* Public API, getting next entry
    /**********************************************************************
     */

    /**
     * Method that will check size of the next entry, if buffer has entries;
     * returns size in bytes if there is at least one entry, or -1 if buffer
     * is empty.
     * Note that this method does not remove the entry and can be called multiple
     * times, that is, it is fully idempotent.
     */
    public abstract int getNextEntryLength();

    /**
     * Method for reading and removing next available entry from buffer.
     * If no entry is available, will block to wait for more data.
     */
    public abstract byte[] getNextEntry() throws InterruptedException;

    /**
     * Method that will read, remove and return next entry, if one is
     * available; or return null if not.
     */
    public abstract byte[] getNextEntryIfAvailable();
    
    /**
     * Method to get (and remove) next entry from the buffer, if one
     * is available. If buffer is empty, may wait up to specified amount
     * of time for new data to arrive.
     * 
     * @param timeoutMsecs Amount of time to wait for more data if
     *   buffer is empty, if non-zero positive number; if zero or
     *   negative number, will return immediately
     *   
     * @return Next entry from buffer, if one was available either
     *   immediately or before waiting for full timeout; or null
     *   if no entry became available
     */
    public abstract byte[] getNextEntry(long timeoutMsecs) throws InterruptedException;

    /*
    /**********************************************************************
    /* Public API, reading next entry in caller-provided array
    /**********************************************************************
     */
    
    /**
     * Method for reading and removing next available entry from buffer and
     * return length of the entry in bytes, if succesful; or, if buffer does
     * not have enough space, return negative number as error code.
     * If no entry is available, will block to wait for more data.
     * 
     * @param buffer Buffer in which entry is to be read: must have enough space
     *  for read to succeed
     * @param offset Offset in buffer to use for storing results
     *
     * @return Length of the entry (non-negative) if read succeeds;
     *   or, negative number that indicates length of the entry in case
     *   of failures: for example, if buffer only had space for 4 bytes,
     *   and entry length was 6, would return -6.
     */
    public abstract int readNextEntry(byte[] buffer, int offset) throws InterruptedException;

    /**
     * Method for reading and removing next available entry from buffer and
     * return length of the entry in bytes, if successful; or, if buffer does
     * not have enough space, return negative number as error code.
     * If no entry is available, will return {@link Integer.MIN_VALUE}.
     * 
     * @param buffer Buffer in which entry is to be read: must have enough space
     *  for read to succeed
     * @param offset Offset in buffer to use for storing results
     *
     * @return {@link Integer#MIN_VALUE} if no entry was available,
     *   length of the entry (non-negative) read if read succeeds,
     *   or negative number that indicates length of the entry in case
     *   of failures: for example, if buffer only had space for 4 bytes,
     *   and entry length was 6, would return -6.
     */
    public abstract int readNextEntryIfAvailable(byte[] buffer, int offset);
    
    /**
     * Method for reading and removing next entry from the buffer, if one
     * is available.
     * If buffer is empty, may wait up to specified amount of time for new data to arrive.
     * If no entry is available after timeout, will return {@link Integer.MIN_VALUE}.
     * If length of entry exceeds available buffer space, will return negative number
     * that indicates length of the entry that would have been copied.
     * 
     * @param timeoutMsecs Amount of time to wait for more data if
     *   buffer is empty, if non-zero positive number; if zero or
     *   negative number, will return immediately
     * @param buffer Buffer in which entry is to be read: must have enough space
     *  for read to succeed
     * @param offset Offset in buffer to use for storing results
     *
     * @return {@link Integer#MIN_VALUE} if no entry was available,
     *   length of the entry (non-negative) read if read succeeds,
     *   or negative number that indicates length of the entry in case
     *   of failures: for example, if buffer only had space for 4 bytes,
     *   and entry length was 6, would return -6.
     */
    public abstract int readNextEntry(long timeoutMsecs, byte[] buffer, int offset)
        throws InterruptedException;
    
    /*
    /**********************************************************************
    /* Public API, read-like access: skipping, peeking, wait-for-next
    /**********************************************************************
     */
    
    /**
     * Method that will read, and return (but NOT remove) the next entry,
     * if one is available; or return null if none available.
     * Method is idempotent.
     *<p>
     * Note that implementations may require additional storage
     * for keeping track of recently peeked entry; for example, they
     * may retain a byte array copy of the contents separate from
     * physical storage.
     */
    public abstract byte[] peekNextEntry();
}
