package com.fasterxml.util.membuf.util;

import java.io.ByteArrayOutputStream;

import org.junit.Assert;

import com.fasterxml.util.membuf.MembufTestBase;

public class UTF8MateTest extends MembufTestBase
{
	public void testSimple() throws Exception
	{
		// "Paivaa" but with umlauts -- 6 characters, of which 3 need 2 bytes
		String INPUT = "P\u00e5iv\u00e5\u00e5";

		byte[] enc = UTF8Mate.encodeAsUTF8(INPUT);
		assertEquals(9, enc.length);
		assertEquals(UTF8Mate.decodeFromUTF8(enc), INPUT);

		// And then using simple stream
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		UTF8Mate.encodeAsUTF8(INPUT, bytes);
		byte[] enc2 = bytes.toByteArray();
		assertEquals(9, enc2.length);
		assertEquals(UTF8Mate.decodeFromUTF8(enc2), INPUT);
	}

	public void testLonger() throws Exception
	{
		// let's do about 2 meg Strings
		final int SIZE = (2 * 1024 * 1024) + 17;
		// almost 2x bytes
		StringBuilder sb = new StringBuilder(SIZE * 2);
		for (int i = 0; i < SIZE; ++i) {
			sb.append(_charFromIndex(i));
		}
		String INPUT = sb.toString();
		byte[] exp = INPUT.getBytes("UTF-8");

		// and then test our code
		byte[] act = UTF8Mate.encodeAsUTF8(INPUT);
		assertEquals(exp.length, act.length);
		Assert.assertArrayEquals(exp, act);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream(exp.length + 16);
		UTF8Mate.encodeAsUTF8(INPUT, bytes);
		byte[] enc2 = bytes.toByteArray();
		assertEquals(enc2.length, act.length);
		Assert.assertArrayEquals(enc2, act);
	}

	private char _charFromIndex(int index) {
		// let's NOT align on 2^ boundaries...
		int x = index % 0xFB;
		
		if (x <= 32) {
			return ' ';
		}
		if (x < 128) {
			return (char) x;
		}
		if (x < 192) {
			return (char) (32 + x);
		}
		return (char) (2048 + x);
	}
}
