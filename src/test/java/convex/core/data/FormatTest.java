package convex.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import convex.core.exceptions.BadFormatException;
import convex.test.Samples;

public class FormatTest {

	@Test public void testVLCLongLength() throws BadFormatException, BufferUnderflowException {
		ByteBuffer bb=ByteBuffer.allocate(100);
		bb.put(Tag.LONG);
		Format.writeVLCLong(bb, Long.MAX_VALUE);
		
		// must be max long length plus tag
		assertEquals(Format.MAX_VLC_LONG_LENGTH+1,bb.position());
		
		bb.flip();
		Blob b=Blob.fromByteBuffer(bb);
		assertEquals(Long.MAX_VALUE,(long)Format.read(b));
;	}
	
//	@Test public void testBigIntegerRegression() throws BadFormatException {
//		BigInteger expected=BigInteger.valueOf(-4223);
//		assertEquals(expected,Format.read("0adf01"));
//		
//		assertThrows(BadFormatException.class,()->Format.read("0affdf01"));
//	}
//	
//	@Test public void testBigIntegerRegression2() throws BadFormatException {
//		BigInteger b=BigInteger.valueOf(1496216);
//		Blob blob=Format.encodedBlob(b);
//		assertEquals(b,Format.read(blob));
//	}
//	
//	@Test public void testBigIntegerRegression3() throws BadFormatException {
//		Blob blob=Blob.fromHex("0a801d");
//		assertThrows(BadFormatException.class,()->Format.read(blob));
//	}
//	
//	@Test public void testBigDecimalRegression() throws BadFormatException {
//		Blob blob=Blob.fromHex("0e001d");
//		BigDecimal bd=Format.read(blob);
//		assertEquals(BigDecimal.valueOf(29),bd);
//		assertEquals(blob,Format.encodedBlob(bd));
//	}
	
	@Test public void testEmbeddedRegression() throws BadFormatException {
		Keyword k=Keyword.create("foo");
		Blob b=Format.encodedBlob(k);
		Object o=Format.read(b);
		assertEquals(k,o);
		assertTrue(Format.isEmbedded(k));
		Ref<?> r=Ref.create(o);
		assertTrue(r.isDirect());
	}
	
//	@Test public void testEmbeddedBigInteger() throws BadFormatException {
//		BigInteger one=BigInteger.ONE;
//		assertFalse(Format.isEmbedded(one));
//		AVector<BigInteger> v=Vectors.of(BigInteger.ONE,BigInteger.TEN);
//		assertFalse(v.getRef(0).isEmbedded());
//		Blob b=Format.encodedBlob(v);
//		AVector<BigInteger> v2=Format.read(b);
//		assertEquals(v,v2);
//		assertEquals(b,Format.encodedBlob(v2));
//	}
	
	@Test public void testBadFormats() throws BadFormatException {
		// test excess high order bits above the long range
		assertEquals(-3717066608267863778L,(long)Format.read("09ccb594f3d1bde9b21e"));
		assertThrows(BadFormatException.class,()->{
			Format.read("09b3ccb594f3d1bde9b21e");
		});
		
		// test excess high bytes for -1
		assertThrows(BadFormatException.class,()->Format.read("09ffffffffffffffffff7f"));

		// test excess high bytes for negative number
		assertEquals(Long.MIN_VALUE,(long)Format.read("09ff808080808080808000"));
		assertThrows(BadFormatException.class,()->Format.read("09ff80808080808080808000"));

	}
	
	@Test public void testStringRegression() throws BadFormatException {
		String s="��zI�&$\\ž1�����4�E4�a8�#?$wD(�#";
		Blob b=Format.encodedBlob(s);
		String s2=Format.read(b);
		assertEquals(s,s2);
	}
	
	@Test public void testListRegression() throws BadFormatException {
		AList<Object> l=Lists.of(Blobs.fromHex("41da2aa427dc50775dd0b077"), -1449690165);
		
		Blob b=Format.encodedBlob(l);
		AList<Object> l2=Format.read(b);
		
		assertEquals(l,l2);
	}
	
	@Test public void testMalformedStrings() {
		// bad examples constructed using info from https://www.w3.org/2001/06/utf-8-wrong/UTF-8-test.html
		assertThrows(BadFormatException.class,()->Format.read("300180")); // continuation only
		assertThrows(BadFormatException.class,()->Format.read("3001FF")); 
	}
	
	@Test public void testCanonical() {
		assertTrue(Format.isCanonical(Vectors.empty()));
		assertTrue(Format.isCanonical(null));
		assertTrue(Format.isCanonical(1));
		assertTrue(Format.isCanonical(Blob.create(new byte[1000]))); // should be OK
		assertFalse(Format.isCanonical(Blob.create(new byte[10000]))); // too big to be canonical
		
		assertThrows(Error.class,()->Format.isCanonical(new ArrayList<Object>())); // a random class
		assertThrows(Error.class,()->Format.isCanonical(new AtomicLong(10L))); // a random Number subclass
	}
	
	@Test public void testReadBlobData() throws BadFormatException {
		Blob d=Blob.fromHex("cafebabe");
		Blob edData=Format.encodedBlob(d);
		AArrayBlob dd=Format.read(edData);
		assertEquals(d,dd);
		assertSame(edData,dd.getEncoding()); // should re-use encoded data object directly
	}
	
	@Test
	public void testBadMessageTooLong() throws BadFormatException {
		Object o=Samples.FOO;
		Blob data=Format.encodedBlob(o).append(Blob.fromHex("ff")).toBlob();
		assertThrows(BadFormatException.class,()->Format.read(data));
	}
	
	@Test
	public void testMessageLength() throws BadFormatException {
		// empty bytebuffer, therefore no message lengtg
		ByteBuffer bb1=Blob.fromHex("").toByteBuffer();
		assertThrows(BadFormatException.class,()->Format.peekMessageLength(bb1));
		
		// bad first byte! Needs to carry if 0x40 or more
		ByteBuffer bb2=Blob.fromHex("43").toByteBuffer();
		assertThrows(BadFormatException.class,()->Format.peekMessageLength(bb2));
		
		// maximum message length 
		ByteBuffer bb2a=Blob.fromHex("BF7F").toByteBuffer();
		assertEquals(Format.LIMIT_ENCODING_LENGTH,Format.peekMessageLength(bb2a));

		// overflow message length
		Blob overflow=Blob.fromHex("C000");
		ByteBuffer bb2aa=overflow.toByteBuffer();
		assertThrows(BadFormatException.class,()->Format.peekMessageLength(bb2aa));
		
		ByteBuffer bb2b=Blob.fromHex("8043").toByteBuffer();
		assertEquals(67,Format.peekMessageLength(bb2b));

		
		ByteBuffer bb3=Blob.fromHex("FFFF").toByteBuffer();
		assertThrows(BadFormatException.class,()->Format.peekMessageLength(bb3));
	}
	
	@Test 
	public void testWriteRef() {
		// TODO: consider whether this is valid
		// shouldn't be allowed to write a Ref directly as a top-level message
		// ByteBuffer b=ByteBuffer.allocate(10);
		// assertThrows(IllegalArgumentException.class,()->Format.write(b, Ref.create("foo")));
	}
}
