package com.fasterxml.util.membuf;

import java.util.Arrays;

import junit.framework.TestCase;

public abstract class MembufTestBase extends TestCase
{
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