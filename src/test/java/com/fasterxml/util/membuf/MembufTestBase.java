package com.fasterxml.util.membuf;

import java.util.Arrays;

import com.fasterxml.util.membuf.base.BytesSegment;
import com.fasterxml.util.membuf.base.LongsSegment;
import com.fasterxml.util.membuf.impl.*;

import junit.framework.TestCase;

public abstract class MembufTestBase extends TestCase
{
    public enum SegType {
        BYTE_BUFFER_DIRECT,
        BYTE_BUFFER_FAKE,
        BYTE_ARRAY;
    }

    /*
    /**********************************************************************
    /* Factory methods
    /**********************************************************************
     */
    
    protected MemBuffersForBytes createBytesBuffers(SegType a, int segLen, int minSegs, int maxSegs)
    {
        SegmentAllocator<BytesSegment> all;
        switch (a) {
        case BYTE_BUFFER_DIRECT:
            all = ByteBufferBytesSegment.allocator(segLen, minSegs, maxSegs, true);
            break;
        case BYTE_BUFFER_FAKE:
            all = ByteBufferBytesSegment.allocator(segLen, minSegs, maxSegs, false);
            break;
        case BYTE_ARRAY:
            all = ArrayBytesSegment.allocator(segLen, minSegs, maxSegs);
            break;
        default:
            throw new Error();
        }
        return new MemBuffersForBytes(all);
    }

    protected MemBuffersForLongs createLongsBuffers(SegType a, int segLen, int minSegs, int maxSegs)
    {
        SegmentAllocator<LongsSegment> all;
        switch (a) {
        case BYTE_BUFFER_DIRECT:
            all = ByteBufferLongsSegment.allocator(segLen, minSegs, maxSegs, true);
            break;
        case BYTE_BUFFER_FAKE:
            all = ByteBufferLongsSegment.allocator(segLen, minSegs, maxSegs, false);
            break;
        case BYTE_ARRAY:
            all = ArrayLongsSegment.allocator(segLen, minSegs, maxSegs);
            break;
        default:
            throw new Error();
        }
        return new MemBuffersForLongs(all);
    }
    
    public byte[] buildBytesChunk(int length)
    {
        byte[] result = new byte[length];
        for (int i = 0; i < length; ++i) {
            result[i] = (byte) i;
        }
        return result;
    }

    public long[] buildLongsChunk(int length)
    {
        long[] result = new long[length];
        for (int i = 0; i < length; ++i) {
            result[i] = (long) i;
        }
        return result;
    }

    /*
    /**********************************************************************
    /* Data chunk verification
    /**********************************************************************
     */
    
    public void verifyChunk(byte[] chunk)
    {
        verifyChunkPrefix(chunk, chunk.length);
    }
    
    public void verifyChunk(byte[] chunk, int expLength)
    {
        if (chunk.length != expLength) {
            fail("Failure for block: length is "+chunk.length+", expected "+expLength);
        }
        verifyChunk(chunk);
    }

    public void verifyChunkPrefix(byte[] chunk, int prefixLength)
    {
        verifyChunkPrefix(chunk, 0, prefixLength);
    }

    public void verifyChunkPrefix(byte[] chunk, int offset, int prefixLength)
    {
        for (int i = 0; i < prefixLength; ++i) {
            int index = offset+i;
            int act = chunk[index] & 0xFF;
            int exp = i & 0xFF;
            if (act != exp) {
                fail("Failure for block of length "+prefixLength+"; byte #"+index+" not 0x"+Integer.toHexString(exp)
                        +" as expected but 0x"+Integer.toHexString(act));
            }
        }
    }

    public void verifyChunk(long[] chunk)
    {
        verifyChunkPrefix(chunk, chunk.length);
    }
    
    public void verifyChunk(long[] chunk, int expLength)
    {
        if (chunk.length != expLength) {
            fail("Failure for block: length is "+chunk.length+", expected "+expLength);
        }
        verifyChunk(chunk);
    }

    public void verifyChunkPrefix(long[] chunk, int prefixLength)
    {
        verifyChunkPrefix(chunk, 0, prefixLength);
    }

    public void verifyChunkPrefix(long[] chunk, int offset, int prefixLength)
    {
        for (int i = 0; i < prefixLength; ++i) {
            int index = offset+i;
            long act = chunk[index];
            long exp = i;
            if (act != exp) {
                fail("Failure for block of length "+prefixLength+"; long #"+index+" not 0x"+Long.toHexString(exp)
                        +" as expected but 0x"+Long.toHexString(act));
            }
        }
    }

    /*
    /**********************************************************************
    /* Verifying exceptions
    /**********************************************************************
     */
    
    protected void verifyException(Throwable e, String... matches)
    {
        String msg = e.getMessage();
        String lmsg = (msg == null) ? "" : msg.toLowerCase();
        for (String match : matches) {
            String lmatch = match.toLowerCase();
            if (lmsg.indexOf(lmatch) >= 0) {
                return;
            }
        }
        fail("Expected an exception with one of substrings ("+Arrays.asList(matches)+"): got one with message \""+msg+"\"");
    }
}