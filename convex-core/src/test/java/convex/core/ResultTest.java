package convex.core;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.Keyword;
import convex.core.data.Keywords;
import convex.core.data.Maps;
import convex.core.data.RecordTest;
import convex.core.data.Vectors;
import convex.core.data.prim.CVMLong;
import convex.core.lang.RT;

public class ResultTest {

	@Test
	public void testBasicResult() {
		Result r1=Result.create(RT.cvm(0L),Vectors.of(1,2,3));
		assertNull(r1.getTrace());
		assertNull(r1.getInfo());
		
		assertSame(r1,r1.updateRefs(r->r));
		
		RecordTest.doRecordTests(r1);

	}
	
	@Test
	public void testResultCreation() {
		AMap<Keyword,ACell> info=Maps.of(Keywords.TRACE,Vectors.empty());
		Result r1=Result.create(CVMLong.create(0L),RT.cvm(1L),ErrorCodes.FATAL,info);
		assertSame(Vectors.empty(),r1.getTrace());
		assertSame(info,r1.getInfo());
		assertSame(ErrorCodes.FATAL,r1.getErrorCode());
		
		RecordTest.doRecordTests(r1);
	}

}
