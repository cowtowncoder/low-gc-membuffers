package com.fasterxml.util.membuf.util;

import java.lang.ref.SoftReference;

/**
 * Simple helper class that can be used for efficient per-thread
 * recycling of an individual byte buffer.
 * Note that an instance of this class MUST be used as a static member
 * of a class; otherwise per-thread semantics DO NOT WORK as expected,
 * and no recycling occurs.
 *<p>
 * The seeming complexity of this class is due to two levels of indirection
 * we need: one to add per-thread scoping, and the other for "soft references"
 * that allow JVM to collect recycled things as garbage, if and as necessary.
 * Because of this we need an intermediate holder object that caller can then
 * hold on to for life-cycle of its buffer: so basically caller just gets
 * holder once, gets a buffer if it needs one, as well as returns it back
 * to holder when it is done.
 * 
 * @since 1.1
 */
public class BufferRecycler extends ThreadLocal<SoftReference<BufferRecycler.Holder>>
{
    protected final int _initialBufferSize;

    /**
     * @param initialSize Default size of the buffer to allocate,
     *    if no recyclable instance retained.
     */
    public BufferRecycler(int initialSize) {
        _initialBufferSize = initialSize;
    }

    @Override
    protected SoftReference<BufferRecycler.Holder> initialValue() {
        return new SoftReference<Holder>(new Holder(_initialBufferSize));
    }

    /**
     * Method used to get a reference to container object that actually
     * handles the details of buffer recycling.
     */
    public Holder getHolder()
    {
        Holder h = get().get();
        // Regardless of the reason we don't have holder, create replacement...
        if (h == null) {
            h = new Holder(_initialBufferSize);
            set(new SoftReference<Holder>(h));
        }
        return h;
    }
    
    /**
     * Simple container of actual recycled buffer instance
     */
    public static class Holder {
        private final int _initialBufferSize;
        private byte[] _buffer;

        public Holder(int initialSize) {
            _initialBufferSize = initialSize;
        }
        
        public byte[] borrowBuffer() {
            byte[] b = _buffer;
            if (b == null) {
                b = new byte[_initialBufferSize];
            } else {
                _buffer = null;
            }
            return b;
        }

        public byte[] borrowBuffer(int minSize)
        {
            byte[] b = _buffer;
            if (b != null && (b.length >= minSize)) {
                _buffer = null;
                return b;
            }
            return new byte[Math.max(_initialBufferSize, minSize)];
        }
        
        public void returnBuffer(byte[] b) {
            if (_buffer != null) {
                // one simple sanity check
                if (_buffer == b) {
                    throw new IllegalStateException("Trying to double-return a buffer (length: "+b.length+" bytes)");
                }
                // and only overwrite if we got bigger buffer
                if (b.length <= _buffer.length) {
                    return;
                }
            }
            _buffer = b;
        }
    }
}
