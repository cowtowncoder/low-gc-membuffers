package com.fasterxml.util.membuf;

/**
 * Interface that defines API used by helper objects used to implement
 * things like auto-closing buffers. It responds to life-cycle events
 * sent by {@link MemBuffersForBytes} (and other factories that
 * create {link MemBuffer}s) and possibly other components.
 * 
 * @since 0.9.2
 */
public interface MemBufferTracker
{
    /**
     * Helper type, instance of which is passed to tracker
     * {@link MemBuffer} instances, so that they can (and must)
     * report explicit <code>close()</code> calls.
     */
    public interface Token
    {
        /**
         * Method called by buffer when it has been closed.
         */
        public void bufferClosed();
    }

    /**
     * Number of active (non-closed) {@link MemBuffer} instances tracker
     * is aware of. Due to concurrent nature of the system, this number
     * is approximate (that is, number of buffers may change concurrently
     * so that returned value may be inaccurate at the time caller receives it).
     */
    public int getActiveBufferCount();

    /**
     * Method that may be called to try to ensure that tracker state is
     * as up-to-date as possible; including auto-closing of buffers being
     * tracked (for trackers that add auto-closing).
     */
    public void clean();

    /**
     * Method called to notify tracker that a new buffer has been created;
     * and to create a {@link Token} that the buffer should use.
     */
    public Token trackBuffer(MemBuffer b);
}
