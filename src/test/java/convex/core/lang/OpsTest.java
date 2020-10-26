package convex.core.lang;

import static convex.test.Assertions.assertJuiceError;
import static convex.test.Assertions.assertUndeclaredError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import convex.core.State;
import convex.core.data.AMap;
import convex.core.data.AString;
import convex.core.data.Symbol;
import convex.core.data.Syntax;
import convex.core.data.Vectors;
import convex.core.exceptions.InvalidDataException;
import convex.core.lang.impl.AClosure;
import convex.core.lang.impl.Fn;
import convex.core.lang.ops.Cond;
import convex.core.lang.ops.Constant;
import convex.core.lang.ops.Def;
import convex.core.lang.ops.Do;
import convex.core.lang.ops.Invoke;
import convex.core.lang.ops.Lambda;
import convex.core.lang.ops.Let;
import convex.core.lang.ops.Lookup;

/**
 * Tests for ops functionality.
 * 
 * In general, focused on unit testing special op capabilities. General on-chain
 * behaviour should be covered elsewhere.
 */
public class OpsTest {

	private static final State INITIAL = TestState.INITIAL;
	private static final long INITIAL_JUICE = TestState.INITIAL_JUICE;
	private static final Context<?> INITIAL_CONTEXT = TestState.INITIAL_CONTEXT;

	@Test
	public void testConstant() {
		Context<?> c = INITIAL_CONTEXT;

		{// simple long constant
			AOp<Long> op = Constant.create(10L);
			Context<Long> c2 = c.execute(op);

			assertEquals(INITIAL_JUICE - Juice.CONSTANT, c2.getJuice());
			assertEquals(10L, (long) c2.getResult());
		}

		{// null constant
			AOp<Long> op = Constant.nil();
			Context<Long> c2 = c.execute(op);

			assertEquals(INITIAL_JUICE - Juice.CONSTANT, c2.getJuice());
			assertNull(c2.getResult());
		}
	}

	@Test
	public void testOutOfJuice() {
		long JUICE = Juice.CONSTANT - 1; // insufficient juice to run operation
		Context<?> c = Context.createInitial(INITIAL, TestState.HERO, JUICE);

		AOp<Long> op = Constant.create(10L);
		assertJuiceError(c.execute(op));
	}

	@Test
	public void testDef() {
		Context<?> c1 = INITIAL_CONTEXT;

		Symbol fooSym = Symbol.create("foo");
		AOp<AString> op = Def.create(Syntax.create(fooSym), Constant.create("bar"));

		AMap<Symbol, Syntax> env1 = c1.getEnvironment();
		Context<AString> c2 = c1.execute(op);
		AMap<Symbol, Syntax> env2 = c2.getEnvironment();

		assertNotEquals(env1, env2);

		assertNull(env1.get(fooSym)); // initially no entry
		assertEquals("bar", env2.get(fooSym).getValue().toString());

		long expectedJuice = INITIAL_JUICE - Juice.CONSTANT - Juice.DEF;
		assertEquals(expectedJuice, c2.getJuice());
		assertEquals("bar", c2.getResult().toString());

		AOp<AString> lookupOp = Lookup.create(Symbol.create("foo"));
		Context<AString> c3 = c2.execute(lookupOp);
		expectedJuice -= Juice.LOOKUP_DYNAMIC;
		assertEquals(expectedJuice, c3.getJuice());
		assertEquals("bar", c3.getResult().toString());

	}

	@Test
	public void testUndeclaredLookup() {
		Context<?> c = INITIAL_CONTEXT;
		AOp<String> op = Lookup.create("missing-symbol");
		assertUndeclaredError(c.execute(op));
	}

	@Test
	public void testDo() {
		Context<?> c = INITIAL_CONTEXT;

		AOp<AString> op = Do.create(Def.create("foo", Constant.create("bar")), Lookup.create("foo"));

		Context<AString> c2 = c.execute(op);
		long expectedJuice = INITIAL_JUICE - (Juice.CONSTANT + Juice.DEF + Juice.LOOKUP_DYNAMIC + Juice.DO);
		assertEquals(expectedJuice, c2.getJuice());
		assertEquals("bar", c2.getResult().toString());
	}

	@Test
	public void testLet() {
		Context<?> c = INITIAL_CONTEXT;
		AOp<AString> op = Let.create(Vectors.of(Syntax.create(Symbols.FOO)),
				Vectors.of(Constant.create("bar"), Lookup.create("foo")), false);
		Context<AString> c2 = c.execute(op);
		assertEquals("bar", c2.getResult().toString());
	}

	@Test
	public void testCondTrue() {
		Context<?> c = INITIAL_CONTEXT;

		AOp<AString> op = Cond.create(Constant.create(true), Constant.create("trueResult"),
				Constant.create("falseResult"));

		Context<AString> c2 = c.execute(op);

		assertEquals("trueResult", c2.getResult().toString());
		long expectedJuice = INITIAL_JUICE - (Juice.COND_OP + Juice.CONSTANT + Juice.CONSTANT);
		assertEquals(expectedJuice, c2.getJuice());
	}

	@Test
	public void testCondFalse() {
		Context<?> c = INITIAL_CONTEXT;

		AOp<AString> op = Cond.create(Constant.create(false), Constant.create("trueResult"),
				Constant.create("falseResult"));

		Context<AString> c2 = c.execute(op);

		assertEquals("falseResult", c2.getResult().toString());
		long expectedJuice = INITIAL_JUICE - (Juice.COND_OP + Juice.CONSTANT + Juice.CONSTANT);
		assertEquals(expectedJuice, c2.getJuice());
	}

	@Test
	public void testCondNoResult() {
		Context<?> c = INITIAL_CONTEXT;

		AOp<String> op = Cond.create(Constant.create(false), Constant.create("trueResult"));

		Context<String> c2 = c.execute(op);

		assertNull(c2.getResult());
		long expectedJuice = INITIAL_JUICE - (Juice.COND_OP + Juice.CONSTANT);
		assertEquals(expectedJuice, c2.getJuice());
	}

	@Test
	public void testCondEnvironmentChange() {
		Context<?> c = INITIAL_CONTEXT;

		Symbol sym = Symbol.create("val");

		AOp<AString> op = Cond.create(Do.create(Def.create(sym, Constant.create(false)), Constant.create(false)),
				Constant.create("1"), Lookup.create(sym), Constant.create("2"),
				Do.create(Def.create(sym, Constant.create(true)), Constant.create(false)), Constant.create("3"),
				Lookup.create(sym), Constant.create("4"), Constant.create("5"));

		Context<AString> c2 = c.execute(op);
		assertEquals("4", c2.getResult().toString());
	}

	@Test
	public void testInvoke() {
		Context<?> c = INITIAL_CONTEXT;

		Symbol sym = Symbol.create("arg0");

		Invoke<AString> op = Invoke.create(Lambda.create(Vectors.of(Syntax.create(sym)), Lookup.create(sym)),
				Constant.create("bar"));

		Context<AString> c2 = c.execute(op);
		assertEquals("bar", c2.getResult().toString());
	}
	
	@Test
	public void testLookup() throws InvalidDataException {
		Lookup<?> l1=Lookup.create("foo");
		l1.validateCell();
		assertNull(l1.getAddress());
		
		Lookup<?> l2=Lookup.create(Core.CORE_ADDRESS,"count");
		l2.validateCell();
		assertSame(Core.CORE_ADDRESS,l2.getAddress());
	}

	@Test
	public void testLambda() {
		Context<?> c = INITIAL_CONTEXT;

		Symbol sym = Symbol.create("arg0");

		Lambda<Object> lam = Lambda.create(Vectors.of(Syntax.create(sym)), Lookup.create(sym));

		Context<AClosure<Object>> c2 = c.execute(lam);
		AClosure<Object> fn = c2.getResult();
		assertTrue(fn.hasArity(1));
		assertFalse(fn.hasArity(2));
	}
	
	@Test
	public void testLambdaString() {
		Fn<Object> fn = Fn.create(Vectors.empty(), Constant.nil());
		assertEquals("(fn [] nil)",fn.toString());
	}

}
