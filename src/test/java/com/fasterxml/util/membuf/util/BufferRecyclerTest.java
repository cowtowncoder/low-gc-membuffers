package com.fasterxml.util.membuf.util;

import com.fasterxml.util.membuf.MembufTestBase;

public class BufferRecyclerTest extends MembufTestBase
{
	final static BufferRecycler recycler = new BufferRecycler(1000);

	/**
	 * NOTE: due to static state, test must not be run more than once
	 * per JVM.
	 */
	public void testSimple()
	{
		BufferRecycler.Holder h = recycler.getHolder();
		assertNotNull(h);
		byte[] b = h.borrowBuffer();
		assertEquals(1000, b.length);
		// first: return it as is...
		h.returnBuffer(b);
		byte[] b2 = h.borrowBuffer();
		assertSame(b, b2);
		
		// but then return buffer of different size...
		h.returnBuffer(new byte[1200]);

		byte[] c = h.borrowBuffer();
		assertEquals(1200, c.length);
	}
}
