package com.fasterxml.util.membuf.util;

import java.io.*;
import java.lang.ref.SoftReference;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Helper class used for efficient encoding of Java Strings as UTF-8 encoded
 * byte streams; and decoding of such streams back as Strings.
 *<p>
 * Note that methods in here are somewhat optimized, but not ridiculously so.
 * 
 * @since 1.1
 */
public final class UTF8Mate
{
    // Size of the initial block, when starting from scratch
    private final static int INITIAL_BLOCK_SIZE = 500;
    
    private final static int SURR1_FIRST = 0xD800;
    private final static int SURR1_LAST = 0xDBFF;
    private final static int SURR2_FIRST = 0xDC00;
    private final static int SURR2_LAST = 0xDFFF;
    
    /**
     * Helper object used for efficient recycling of encoding buffers.
     */
    final protected static ThreadLocal<SoftReference<UTF8Mate>> _threadEncoder
        = new ThreadLocal<SoftReference<UTF8Mate>>();

    /**
     * We will reuse encoding buffer for performance; works because encoders are
     * reused with <code>ThreadLocal</code>
     */
    protected byte[] _encodingBuffer;
    
    /*
    /**********************************************************************
    /* Construction, instance access
    /**********************************************************************
     */
    
    private UTF8Mate() {
        _encodingBuffer = new byte[INITIAL_BLOCK_SIZE];
    }

    /*
    /**********************************************************************
    /* Public API, encoding (String -> UTF-8 bytes)
    /**********************************************************************
     */
    
    /**
     * Will encode given String as UTF-8, return resulting byte array.
     */
    public static byte[] encodeAsUTF8(String text)
    {
        if (text == null) {
            return null;
        }
        SoftReference<UTF8Mate> ref = _threadEncoder.get();
        UTF8Mate enc = (ref == null) ? null : ref.get();

        if (enc == null) {
            enc = new UTF8Mate();
            _threadEncoder.set(new SoftReference<UTF8Mate>(enc));
        }
        return enc._encodeAsUTF8(text);
    }

    /**
     * Will encode given String as UTF-8, prepend it with a given
     * prefix, and return resulting byte array.
     */
    public static byte[] encodeAsUTF8(byte[] prefix, String text)
    {
        SoftReference<UTF8Mate> ref = _threadEncoder.get();
        UTF8Mate enc = (ref == null) ? null : ref.get();

        if (enc == null) {
            enc = new UTF8Mate();
            _threadEncoder.set(new SoftReference<UTF8Mate>(enc));
        }
        return enc._encodeAsUTF8(prefix, text);
    }

    /**
     * Will encode given String as UTF-8, after allocation of a fixed
     * length of empty space (left as null bytes), and also add optional
     * suffix empty space;
     * and return resulting byte array.
     * 
     * @param fillWithNulls If true, will ensure that prefix and suffix sections
     *    are initialized to null bytes; if false, may contain any data
     */
    public static byte[] encodeAsUTF8(String text, int prefixLength, int suffixLength,
            boolean fillWithNulls)
    {
        SoftReference<UTF8Mate> ref = _threadEncoder.get();
        UTF8Mate enc = (ref == null) ? null : ref.get();

        if (enc == null) {
            enc = new UTF8Mate();
            _threadEncoder.set(new SoftReference<UTF8Mate>(enc));
        }
        return enc._encodeAsUTF8(text, prefixLength, suffixLength, fillWithNulls);
    }
    
    public static OutputStream encodeAsUTF8(String text, OutputStream out) throws IOException
    {
        if (text != null) {
            SoftReference<UTF8Mate> ref = _threadEncoder.get();
            UTF8Mate enc = (ref == null) ? null : ref.get();

            if (enc == null) {
                enc = new UTF8Mate();
                _threadEncoder.set(new SoftReference<UTF8Mate>(enc));
            }
            enc._encodeAsUTF8(text, out);
        }
        return out;
    }
    
    private final static Charset UTF8 = Charset.forName("UTF-8");

    /*
    /**********************************************************************
    /* Public API, decoding (UTF-8 bytes -> String)
    /*
    /* NOTE: not yet optimized, should be!
    /**********************************************************************
     */
    
    public static String decodeFromUTF8(byte[] bytes)
    {
        if (bytes == null) {
            return null;
        }
        return decodeFromUTF8(bytes, 0, bytes.length);
    }
    
    public static String decodeFromUTF8(byte[] bytes, int offset, int length)
    {
        // !!! TODO: optimize
        return new String(bytes, offset, length, UTF8);
    }

    /*
    /**********************************************************
    /* Internal methods, encoding as UTF-8
    /**********************************************************
     */

    private byte[] _encodeAsUTF8(String text)
    {
        int inputPtr = 0;
        int inputEnd = text.length();
        int outputPtr = 0;
        byte[] outputBuffer = _encodingBuffer;
        int outputEnd = outputBuffer.length;
        
        final ByteArrayBuilder byteBuilder = new ByteArrayBuilder(_encodingBuffer);
        
        main_loop:
        while (inputPtr < inputEnd) {
            int c = text.charAt(inputPtr++);

            // first tight loop for ascii
            while (c <= 0x7F) {
                if (outputPtr >= outputEnd) {
                    outputBuffer = byteBuilder.finishCurrentSegment();
                    outputEnd = outputBuffer.length;
                    outputPtr = 0;
                }
                outputBuffer[outputPtr++] = (byte) c;
                if (inputPtr >= inputEnd) {
                    break main_loop;
                }
                c = text.charAt(inputPtr++);
            }

            // then multi-byte...
            if (outputPtr >= outputEnd) {
                outputBuffer = byteBuilder.finishCurrentSegment();
                outputEnd = outputBuffer.length;
                outputPtr = 0;
            }
            if (c < 0x800) { // 2-byte
                outputBuffer[outputPtr++] = (byte) (0xc0 | (c >> 6));
            } else { // 3 or 4 bytes
                // Surrogates?
                if (c < SURR1_FIRST || c > SURR2_LAST) { // nope
                    outputBuffer[outputPtr++] = (byte) (0xe0 | (c >> 12));
                    if (outputPtr >= outputEnd) {
                        outputBuffer = byteBuilder.finishCurrentSegment();
                        outputEnd = outputBuffer.length;
                        outputPtr = 0;
                    }
                    outputBuffer[outputPtr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                } else { // yes, surrogate pair
                    if (c > SURR1_LAST) { // must be from first range
                        _throwIllegalSurrogate(c);
                    }
                    // and if so, followed by another from next range
                    if (inputPtr >= inputEnd) {
                        _throwIllegalSurrogate(c);
                    }
                    c = _convertSurrogate(c, text.charAt(inputPtr++));
                    if (c > 0x10FFFF) { // illegal, as per RFC 4627
                        _throwIllegalSurrogate(c);
                    }
                    outputBuffer[outputPtr++] = (byte) (0xf0 | (c >> 18));
                    if (outputPtr >= outputEnd) {
                        outputBuffer = byteBuilder.finishCurrentSegment();
                        outputEnd = outputBuffer.length;
                        outputPtr = 0;
                    }
                    outputBuffer[outputPtr++] = (byte) (0x80 | ((c >> 12) & 0x3f));
                    if (outputPtr >= outputEnd) {
                        outputBuffer = byteBuilder.finishCurrentSegment();
                        outputEnd = outputBuffer.length;
                        outputPtr = 0;
                    }
                    outputBuffer[outputPtr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                }
            }
            if (outputPtr >= outputEnd) {
                outputBuffer = byteBuilder.finishCurrentSegment();
                outputEnd = outputBuffer.length;
                outputPtr = 0;
            }
            outputBuffer[outputPtr++] = (byte) (0x80 | (c & 0x3f));
        }
        
        // let's ensure we are reusing the largest chunk:
        _encodingBuffer = byteBuilder.getCurrentSegment();
        return byteBuilder.complete(outputPtr);
    }

    private void _encodeAsUTF8(String text, OutputStream out) throws IOException
    {
        int inputPtr = 0;
        final int inputEnd = text.length();
        int outputPtr = 0;
        final byte[] outputBuffer = _encodingBuffer;
        final int outputEnd = outputBuffer.length;
        
        main_loop:
        while (inputPtr < inputEnd) {
            int c = text.charAt(inputPtr++);

            while (c <= 0x7F) {
                if (outputPtr >= outputEnd) {
                	out.write(outputBuffer, 0, outputEnd);
                    outputPtr = 0;
                }
                outputBuffer[outputPtr++] = (byte) c;
                if (inputPtr >= inputEnd) {
                    break main_loop;
                }
                c = text.charAt(inputPtr++);
            }

            if (outputPtr >= outputEnd) {
            	out.write(outputBuffer, 0, outputEnd);
                outputPtr = 0;
            }
            if (c < 0x800) { // 2-byte
                outputBuffer[outputPtr++] = (byte) (0xc0 | (c >> 6));
            } else { // 3 or 4 bytes
                // Surrogates?
                if (c < SURR1_FIRST || c > SURR2_LAST) { // nope
                    outputBuffer[outputPtr++] = (byte) (0xe0 | (c >> 12));
                    if (outputPtr >= outputEnd) {
                    	out.write(outputBuffer, 0, outputEnd);
                        outputPtr = 0;
                    }
                    outputBuffer[outputPtr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                } else { // yes, surrogate pair
                    if (c > SURR1_LAST) { // must be from first range
                        _throwIllegalSurrogate(c);
                    }
                    if (inputPtr >= inputEnd) {
                        _throwIllegalSurrogate(c);
                    }
                    c = _convertSurrogate(c, text.charAt(inputPtr++));
                    if (c > 0x10FFFF) { // illegal, as per RFC 4627
                        _throwIllegalSurrogate(c);
                    }
                    outputBuffer[outputPtr++] = (byte) (0xf0 | (c >> 18));
                    if (outputPtr >= outputEnd) {
                    	out.write(outputBuffer, 0, outputEnd);
                        outputPtr = 0;
                    }
                    outputBuffer[outputPtr++] = (byte) (0x80 | ((c >> 12) & 0x3f));
                    if (outputPtr >= outputEnd) {
                    	out.write(outputBuffer, 0, outputEnd);
                        outputPtr = 0;
                    }
                    outputBuffer[outputPtr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                }
            }
            if (outputPtr >= outputEnd) {
            	out.write(outputBuffer, 0, outputEnd);
                outputPtr = 0;
            }
            outputBuffer[outputPtr++] = (byte) (0x80 | (c & 0x3f));
        }
        if (outputPtr > 0) {
        	out.write(outputBuffer, 0, outputPtr);
        }
    }
    
    private byte[] _encodeAsUTF8(byte[] prefix, String text)
    {
        int inputPtr = 0;
        int inputEnd = text.length();
        int outputPtr = prefix.length;

        if (outputPtr > _encodingBuffer.length) {
            _encodingBuffer = new byte[outputPtr];
        }
        System.arraycopy(prefix, 0, _encodingBuffer, 0, outputPtr);
        
        final ByteArrayBuilder byteBuilder = new ByteArrayBuilder(_encodingBuffer);
        byte[] outputBuffer = byteBuilder.getCurrentSegment();
        int outputEnd = outputBuffer.length;
        
        main_loop:
        while (inputPtr < inputEnd) {
            int c = text.charAt(inputPtr++);

            // first tight loop for ascii
            while (c <= 0x7F) {
                if (outputPtr >= outputEnd) {
                    outputBuffer = byteBuilder.finishCurrentSegment();
                    outputEnd = outputBuffer.length;
                    outputPtr = 0;
                }
                outputBuffer[outputPtr++] = (byte) c;
                if (inputPtr >= inputEnd) {
                    break main_loop;
                }
                c = text.charAt(inputPtr++);
            }

            // then multi-byte...
            if (outputPtr >= outputEnd) {
                outputBuffer = byteBuilder.finishCurrentSegment();
                outputEnd = outputBuffer.length;
                outputPtr = 0;
            }
            if (c < 0x800) { // 2-byte
                outputBuffer[outputPtr++] = (byte) (0xc0 | (c >> 6));
            } else { // 3 or 4 bytes
                // Surrogates?
                if (c < SURR1_FIRST || c > SURR2_LAST) { // nope
                    outputBuffer[outputPtr++] = (byte) (0xe0 | (c >> 12));
                    if (outputPtr >= outputEnd) {
                        outputBuffer = byteBuilder.finishCurrentSegment();
                        outputEnd = outputBuffer.length;
                        outputPtr = 0;
                    }
                    outputBuffer[outputPtr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                } else { // yes, surrogate pair
                    if (c > SURR1_LAST) { // must be from first range
                        _throwIllegalSurrogate(c);
                    }
                    // and if so, followed by another from next range
                    if (inputPtr >= inputEnd) {
                        _throwIllegalSurrogate(c);
                    }
                    c = _convertSurrogate(c, text.charAt(inputPtr++));
                    if (c > 0x10FFFF) { // illegal, as per RFC 4627
                        _throwIllegalSurrogate(c);
                    }
                    outputBuffer[outputPtr++] = (byte) (0xf0 | (c >> 18));
                    if (outputPtr >= outputEnd) {
                        outputBuffer = byteBuilder.finishCurrentSegment();
                        outputEnd = outputBuffer.length;
                        outputPtr = 0;
                    }
                    outputBuffer[outputPtr++] = (byte) (0x80 | ((c >> 12) & 0x3f));
                    if (outputPtr >= outputEnd) {
                        outputBuffer = byteBuilder.finishCurrentSegment();
                        outputEnd = outputBuffer.length;
                        outputPtr = 0;
                    }
                    outputBuffer[outputPtr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                }
            }
            if (outputPtr >= outputEnd) {
                outputBuffer = byteBuilder.finishCurrentSegment();
                outputEnd = outputBuffer.length;
                outputPtr = 0;
            }
            outputBuffer[outputPtr++] = (byte) (0x80 | (c & 0x3f));
        }
        
        // let's ensure we are reusing the largest chunk:
        _encodingBuffer = byteBuilder.getCurrentSegment();
        return byteBuilder.complete(outputPtr);
    }

    private byte[] _encodeAsUTF8(String text, int prefixLen, int suffixLen,
            boolean clearPrefix)
    {
        int inputPtr = 0;
        int inputEnd = text.length();
        int outputPtr = prefixLen;

        if (outputPtr >= _encodingBuffer.length) {
            _encodingBuffer = new byte[outputPtr + Math.min(128, inputEnd << 1)];
        } else if (clearPrefix) {
            Arrays.fill(_encodingBuffer, 0, outputPtr, (byte) 0);
        }
        
        final ByteArrayBuilder byteBuilder = new ByteArrayBuilder(_encodingBuffer);
        byte[] outputBuffer = byteBuilder.getCurrentSegment();
        int outputEnd = outputBuffer.length;
        
        main_loop:
        while (inputPtr < inputEnd) {
            int c = text.charAt(inputPtr++);

            // first tight loop for ascii
            while (c <= 0x7F) {
                if (outputPtr >= outputEnd) {
                    outputBuffer = byteBuilder.finishCurrentSegment();
                    outputEnd = outputBuffer.length;
                    outputPtr = 0;
                }
                outputBuffer[outputPtr++] = (byte) c;
                if (inputPtr >= inputEnd) {
                    break main_loop;
                }
                c = text.charAt(inputPtr++);
            }

            // then multi-byte...
            if (outputPtr >= outputEnd) {
                outputBuffer = byteBuilder.finishCurrentSegment();
                outputEnd = outputBuffer.length;
                outputPtr = 0;
            }
            if (c < 0x800) { // 2-byte
                outputBuffer[outputPtr++] = (byte) (0xc0 | (c >> 6));
            } else { // 3 or 4 bytes
                // Surrogates?
                if (c < SURR1_FIRST || c > SURR2_LAST) { // nope
                    outputBuffer[outputPtr++] = (byte) (0xe0 | (c >> 12));
                    if (outputPtr >= outputEnd) {
                        outputBuffer = byteBuilder.finishCurrentSegment();
                        outputEnd = outputBuffer.length;
                        outputPtr = 0;
                    }
                    outputBuffer[outputPtr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                } else { // yes, surrogate pair
                    if (c > SURR1_LAST) { // must be from first range
                        _throwIllegalSurrogate(c);
                    }
                    // and if so, followed by another from next range
                    if (inputPtr >= inputEnd) {
                        _throwIllegalSurrogate(c);
                    }
                    c = _convertSurrogate(c, text.charAt(inputPtr++));
                    if (c > 0x10FFFF) { // illegal, as per RFC 4627
                        _throwIllegalSurrogate(c);
                    }
                    outputBuffer[outputPtr++] = (byte) (0xf0 | (c >> 18));
                    if (outputPtr >= outputEnd) {
                        outputBuffer = byteBuilder.finishCurrentSegment();
                        outputEnd = outputBuffer.length;
                        outputPtr = 0;
                    }
                    outputBuffer[outputPtr++] = (byte) (0x80 | ((c >> 12) & 0x3f));
                    if (outputPtr >= outputEnd) {
                        outputBuffer = byteBuilder.finishCurrentSegment();
                        outputEnd = outputBuffer.length;
                        outputPtr = 0;
                    }
                    outputBuffer[outputPtr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                }
            }
            if (outputPtr >= outputEnd) {
                outputBuffer = byteBuilder.finishCurrentSegment();
                outputEnd = outputBuffer.length;
                outputPtr = 0;
            }
            outputBuffer[outputPtr++] = (byte) (0x80 | (c & 0x3f));
        }
        // let's ensure we are reusing the largest chunk:
        _encodingBuffer = byteBuilder.getCurrentSegment();
        return byteBuilder.complete(outputPtr, suffixLen);
    }    
    /*
    /**********************************************************
    /* Internal helper methods
    /**********************************************************
     */

    /**
     * Method called to calculate UTF code point, from a surrogate pair.
     */
    private int _convertSurrogate(int firstPart, int secondPart)
    {
        // Ok, then, is the second part valid?
        if (secondPart < SURR2_FIRST || secondPart > SURR2_LAST) {
            throw new IllegalArgumentException("Broken surrogate pair: first char 0x"+Integer.toHexString(firstPart)+", second 0x"+Integer.toHexString(secondPart)+"; illegal combination");
        }
        return 0x10000 + ((firstPart - SURR1_FIRST) << 10) + (secondPart - SURR2_FIRST);
    }
    
    private void _throwIllegalSurrogate(int code)
    {
        if (code > 0x10FFFF) { // over max?
            throw new IllegalArgumentException("Illegal character point (0x"+Integer.toHexString(code)+") to output; max is 0x10FFFF as per RFC 4627");
        }
        if (code >= SURR1_FIRST) {
            if (code <= SURR1_LAST) { // Unmatched first part (closing without second part?)
                throw new IllegalArgumentException("Unmatched first part of surrogate pair (0x"+Integer.toHexString(code)+")");
            }
            throw new IllegalArgumentException("Unmatched second part of surrogate pair (0x"+Integer.toHexString(code)+")");
        }
        // should we ever get this?
        throw new IllegalArgumentException("Illegal character point (0x"+Integer.toHexString(code)+") to output");
    }

    /*
    /**********************************************************************
    /* Helper classes
    /**********************************************************************
     */

    /**
     * Helper class that is similar to {@link java.io.ByteArrayOutputStream}
     * in usage, but more geared to specific use case we have here.
     * Specific changes include segment storage (no need to have linear
     * backing buffer, can avoid reallocs, copying), as well API
     * not necessarily based on {@link java.io.OutputStream}.
     * In short, this is a very much specialized builder object.
     */
    private final static class ByteArrayBuilder
    {
        private final static byte[] NO_BYTES = new byte[0];
        
        /**
         * Maximum block size we will use for individual non-aggregated
         * blocks. Let's limit to using 256k chunks.
         */
        private final static int MAX_BLOCK_SIZE = (1 << 18);

        private LinkedList<byte[]> _pastBlocks = null;
        
        /**
         * Number of bytes within byte arrays in {@link _pastBlocks}.
         */
        private int _pastLen;

        private byte[] _currBlock;
        
        public ByteArrayBuilder(byte[] firstBlock) {
            _currBlock = firstBlock;
        }
        
        /**
         * Method called when results are finalized and we can get the
         * full aggregated result buffer to return to the caller
         */
        public byte[] complete(int lastBlockLength)
        {
            int totalLen = _pastLen + lastBlockLength;

            if (totalLen == 0) { // quick check: nothing aggregated?
                return NO_BYTES;
            }
            byte[] result = new byte[totalLen];
            int offset = 0;

            if (_pastBlocks != null) {
                for (byte[] block : _pastBlocks) {
                    int len = block.length;
                    System.arraycopy(block, 0, result, offset, len);
                    offset += len;
                }
            }
            if (lastBlockLength > 0) {
                System.arraycopy(_currBlock, 0, result, offset, lastBlockLength);
                offset += lastBlockLength;
            }
            if (offset != totalLen) { // just a sanity check
                throw new RuntimeException("Internal error: total len assumed to be "+totalLen+", copied "+offset+" bytes");
            }
            return result;
        }

        /**
         * Alternative completion method that will add specific amount of 'fluff'
         * (null bytes) after encoding result.
         * 
         * @param fluffToAppend Number of null bytes to append after buffered
         *    content
         */
        public byte[] complete(int lastBlockLength, int fluffToAppend)
        {
            int totalLen = _pastLen + lastBlockLength + fluffToAppend;

            if (totalLen == 0) { // quick check: nothing aggregated?
                return NO_BYTES;
            }
            byte[] result = new byte[totalLen];
            int offset = 0;

            if (_pastBlocks != null) {
                for (byte[] block : _pastBlocks) {
                    int len = block.length;
                    System.arraycopy(block, 0, result, offset, len);
                    offset += len;
                }
            }
            if (lastBlockLength > 0) {
                System.arraycopy(_currBlock, 0, result, offset, lastBlockLength);
                offset += lastBlockLength;
            }
            offset += fluffToAppend;
            if (offset != totalLen) { // just a sanity check
                throw new RuntimeException("Internal error: total len assumed to be "+totalLen+", copied "+offset+" bytes");
            }
            return result;
        }
        
        public byte[] getCurrentSegment() {
            return _currBlock;
        }

        public byte[] finishCurrentSegment() {
            _allocMore();
            return _currBlock;
        }
        
        private void _allocMore()
        {
            _pastLen += _currBlock.length;

            /* Let's allocate block that's half the total size, except
             * never smaller than twice the initial block size.
             * The idea is just to grow with reasonable rate, to optimize
             * between minimal number of chunks and minimal amount of
             * wasted space.
             */
            int newSize = Math.max((_pastLen >> 1), (INITIAL_BLOCK_SIZE + INITIAL_BLOCK_SIZE));
            // plus not to exceed max we define...
            if (newSize > MAX_BLOCK_SIZE) {
                newSize = MAX_BLOCK_SIZE;
            }
            if (_pastBlocks == null) {
                _pastBlocks = new LinkedList<byte[]>();
            }
            _pastBlocks.add(_currBlock);
            _currBlock = new byte[newSize];
        }
    }
}
