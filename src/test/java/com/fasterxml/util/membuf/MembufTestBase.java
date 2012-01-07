package com.fasterxml.util.membuf;

import java.util.Arrays;

import com.fasterxml.util.membuf.base.BytesSegment;
import com.fasterxml.util.membuf.impl.BytesArraySegmentAllocator;
import com.fasterxml.util.membuf.impl.ByteBufferSegmentAllocator;

import junit.framework.TestCase;

public abstract class MembufTestBase extends TestCase
{
    public enum Allocator {
        BYTE_BUFFER_DIRECT,
        BYTE_BUFFER_FAKE,
        BYTE_ARRAY;
    }

    protected BytesMemBuffers createBuffers(Allocator a, int segLen, int minSegs, int maxSegs)
    {
        SegmentAllocator<BytesSegment> all;
        switch (a) {
        case BYTE_BUFFER_DIRECT:
            all = new ByteBufferSegmentAllocator(segLen, minSegs, maxSegs, true);
            break;
        case BYTE_BUFFER_FAKE:
            all = new ByteBufferSegmentAllocator(segLen, minSegs, maxSegs, false);
            break;
        case BYTE_ARRAY:
            all = new BytesArraySegmentAllocator(segLen, minSegs, maxSegs);
            break;
        default:
            throw new Error();
        }
        return new BytesMemBuffers(all);
    }
    
    public byte[] buildChunk(int length)
    {
        byte[] result = new byte[length];
        for (int i = 0; i < length; ++i) {
            result[i] = (byte) i;
        }
        return result;
    }

    public void verifyChunk(byte[] chunk, int expLength)
    {
        if (chunk.length != expLength) {
            fail("Failure for block: length is "+chunk.length+", expected "+expLength);
        }
        verifyChunk(chunk);
    }
    
    public void verifyChunk(byte[] chunk)
    {
        for (int i = 0, length = chunk.length; i < length; ++i) {
            int act = chunk[i] & 0xFF;
            int exp = i & 0xFF;
            if (act != exp) {
                fail("Failure for block of length "+length+"; byte #"+i+" not 0x"+Integer.toHexString(exp)
                        +" as expected but 0x"+Integer.toHexString(act));
            }
        }
    }

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