package com.fasterxml.util.membuf;

/**
 * Interface that defines decorators that may be registered to
 * decorate {@link MemBuffer} instances created.
 * Decoration is typically used to add features such as auto-closing,
 * or logging.
 * 
 * @since 0.9.1
 */
public interface MemBufferDecorator<T extends MemBuffer>
{
    /**
     * Method called after construction of the source buffer, to let
     * decorator create a new instance or pass the original as-is.
     */
    public T decorateMemBuffer(T original);
}
