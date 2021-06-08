package convex.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import convex.core.Constants;
import convex.core.exceptions.BadFormatException;
import convex.core.exceptions.InvalidDataException;
import convex.core.store.AStore;
import convex.core.store.MemoryStore;
import convex.core.store.Stores;
import convex.core.util.Utils;

/**
 * Generic test functions for arbitrary Data Objects.
 */
public class ObjectsTest {

	/**
	 * Generic tests for a Cell
	 * 
	 * @param a
	 * @throws InvalidDataException
	 */
	public static void doCellTests(ACell a) {
		if (a==null) return;
		
		String edn = a.ednString();
		assertNotNull(edn);

		try {
			a.validateCell();
			// doCellStorageTest(a); // TODO: Maybe fix after we have ACell.toDirect()
		} catch (InvalidDataException e) {
			throw Utils.sneakyThrow(e);
		}
		
		if (a.getRefCount()>0) {
			doRefContainerTests(a);
		}
		
		doCellRefTests(a);
	}

	private static void doCellRefTests(ACell a) {
		Ref<ACell> cachedRef=a.cachedRef;
		Ref<ACell> ref=a.getRef();
		if (cachedRef!=null) assertSame(ref,cachedRef);
		assertSame(ref,a.getRef());
		
		assertEquals(a.isEmbedded(),ref.isEmbedded());
		
		Ref<ACell> refD=ref.toDirect();
		assertTrue(ref.equalsValue(refD));
		assertTrue(refD.equalsValue(ref));
		
	}

	@SuppressWarnings("unused")
	private static void doCellStorageTest(ACell a) throws InvalidDataException {
		
		AStore temp=Stores.current();
		try {
			// test using a new memory store
			MemoryStore ms=new MemoryStore();
			Stores.setCurrent(ms);
			
			Ref<ACell> r=a.getRef();
			
			Hash hash=r.getHash();
			
			assertNull(ms.refForHash(hash));
			
			// persist the Ref
			ACell.createPersisted(a);
			
			// retrieve from store
			Ref<ACell> rr=ms.refForHash(hash);
			
			// should be able to retrieve and validate complete structure
			assertNotNull(rr,()->"Failed to retrieve from store with "+Utils.getClassName(a) + " = "+a);
			ACell b=rr.getValue();
			b.validate();
			assertEquals(a,b);
		} finally {
			Stores.setCurrent(temp);
		}
	}

	/**
	 * Generic tests for any Value
	 * 
	 * @param a
	 */
	public static void doAnyValueTests(ACell a) {
		Hash h=Hash.compute(a);
		
		boolean embedded=Format.isEmbedded(a);
		assertTrue(Format.isCanonical(a));

		Ref<ACell> r = Ref.get(a).persist();
		assertEquals(h,r.getHash());
		assertEquals(a, r.getValue());

		Blob b = Format.encodedBlob(a);
		if (a==null) {
			assertEquals(Blob.NULL_ENCODING,b);
		} else {
			assertEquals(a.getTag(),b.byteAt(0)); // Correct Tag
			assertSame(b,a.getEncoding()); // should be same cached encoding
			assertEquals(b.length,a.getEncodingLength());
		}

		// Any encoding should be less than or equal to the limit
		assertTrue(b.length <= Format.LIMIT_ENCODING_LENGTH);
		
		// If length exceeds MAX_EMBEDDED_LENGTH, cannot be an embedded value
		if (b.length > Format.MAX_EMBEDDED_LENGTH) {
			assertFalse(Format.isEmbedded(a),()->"Testing: "+Utils.getClassName(a)+ " = "+Utils.toString(a));
		}
		
		// tests for memory size
		if (a!=null) {
			long memorySize=a.getMemorySize();
			long encodingSize=a.getEncodingLength();
			int rc=a.getRefCount();
			long childMem=0;
			for (int i=0; i<rc; i++) {
				Ref<ACell> childRef=a.getRef(i);
				long cms=childRef.getMemorySize();
				childMem+=cms;
			}
			if (embedded) {
				assertEquals(memorySize,childMem);
			} else {
				assertEquals(memorySize,encodingSize+childMem+Constants.MEMORY_OVERHEAD);
			}
		}


		try {
			ACell a2;
			a2 = Format.read(b);
			assertEquals(a, a2);
		} catch (BadFormatException e) {
			throw new Error("Can't read encoding: " + b.toHexString(), e);
		}
		
		doCellTests(a);
	}

	/**
	 * Tests for any value implementing the IRefContainer interface
	 * 
	 * @param a
	 */
	private static void doRefContainerTests(ACell a) {
		long tcount = Utils.totalRefCount(a);
		int rcount = Utils.refCount(a);

		assertTrue(rcount <= tcount);
	}

}
