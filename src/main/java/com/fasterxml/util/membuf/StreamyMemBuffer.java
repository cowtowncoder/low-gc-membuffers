package com.fasterxml.util.membuf;

/**
 * Generic type for {@link MemBuffer}s that store data as contiguous
 * sequence of primitive values,
 * so units of appending and reading do not necessarily match.
 * This is similar to JDK stream abstraction.
 *<p>
 * Note that most actual read and append methods are defined in
 * type-specific subtypes (such as {@link StreamyBytesMemBuffer}),
 * since Java does not have a way to define generic primitive types.
 */
public interface StreamyMemBuffer
    extends MemBuffer
{
    /**
     * Method for trying to skip given number of values: will not block,
     * but will skip between 0 and 'skipCount' entries.
     */
    public int skip(int skipCount);

    /**
     * Method for checking maximum length of data that can be read from buffer
     * without blocking.
     * 
     * @return Number of values available, if any (0 if buffer is empty)
     */
    public long available();
}
