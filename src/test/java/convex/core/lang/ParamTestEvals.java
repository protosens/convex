package convex.core.lang;

import static org.junit.Assert.assertEquals;
import static convex.test.Assertions.*;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import convex.core.data.ACell;
import convex.core.data.Address;
import convex.core.data.Blob;
import convex.core.data.Format;
import convex.core.data.Keyword;
import convex.core.data.Ref;
import convex.core.exceptions.BadFormatException;
import convex.core.util.Utils;

@RunWith(Parameterized.class)
public class ParamTestEvals {

	private static long INITIAL_JUICE = TestState.INITIAL_JUICE;
	private static final Context<?> INITIAL_CONTEXT = TestState.INITIAL_CONTEXT.fork();

	private static final Address TEST_CONTRACT = TestState.CONTRACTS[0];

	private static String TC_HEX = TEST_CONTRACT.toHexString();

	@Parameterized.Parameters(name = "{index}: {0}")
	public static Collection<Object[]> dataExamples() {
		return Arrays.asList(new Object[][] { 
				{ "(do)", null }, 
				{ "(do (do :foo))", Keyword.create("foo") },
				{ "(do 1 2)", 2L }, 
				{ "(do 1 *result*)", 1L }, 
				{ "(do (do :foo) (do))", null },
				{ "*result*", null }, 
				{ "*origin*", TestState.HERO }, 
				{ "*caller*", null },
				{ "*address*", TestState.HERO }, 
				{ "(do 1 *result*)", 1L },

				{ "(call \"" + TC_HEX + "\" (:my-address))", TEST_CONTRACT },
				{ "(call \"" + TC_HEX + "\" (\"foo\"))", Keyword.create("bar") },

				{ "(let [a (address \"" + TC_HEX + "\")]" + "(call a (write :bar))" + "(call a (read)))",
						Keyword.create("bar") },

				{ "*depth*", 1L }, // lookup
				{ "(do *depth*)", 2L }, // do, lookup
				{ "(let [a *depth*] a)", 2L }, // let, lookup
				{ "(let [f (fn [] *depth*)] (f))", 3L }, // let, invoke, lookup

				{ "(let [])", null }, { "(let [a 1])", null }, { "(let [a 1] a)", 1L },
				{ "(do (def a 2) (let [a 13] a))", 13L }, { "*juice*", INITIAL_JUICE },
				{ "(- *juice* *juice*)", Juice.LOOKUP_DYNAMIC }, 
				{ "((fn [a] a) 4)", 4L }, { "(do (def a 3) a)", 3L },
				{ "(do (let [a 1] (def f (fn [] a))) (f))", 1L }, { "1", 1L }, { "(not true)", false },
				{ "(= true true)", true } });
	}

	private String source;
	private Object expectedResult;

	public ParamTestEvals(String source, Object expectedResult) {
		this.source = source;
		this.expectedResult = expectedResult;
	}

	public <T extends ACell> AOp<T> compile(String source) {
		try {
			Context<?> c = INITIAL_CONTEXT.fork();
			AOp<T> op = TestState.compile(c, source);
			return op;
		} catch (Exception e) {
			throw Utils.sneakyThrow(e);
		}
	}

	public <T extends ACell> Context<T> eval(AOp<T> op) {
		try {
			Context<?> c = INITIAL_CONTEXT.fork();
			Context<T> rc = c.execute(op);
			return rc;
		} catch (Exception e) {
			throw Utils.sneakyThrow(e);
		}
	}

	public <T extends ACell> Context<T> eval(String source) {
		try {
			Context<?> c = INITIAL_CONTEXT.fork();
			AOp<T> op = TestState.compile(c, source);
			return eval(op);
		} catch (Exception e) {
			throw Utils.sneakyThrow(e);
		}
	}

	@Test
	public void testOpRoundTrip() throws BadFormatException {
		AOp<?> op = compile(source);
		Blob b = Format.encodedBlob(op);
		Ref.createPersisted(op); // persist to allow re-creation

		AOp<?> op2 = Format.read(b);
		Blob b2 = Format.encodedBlob(op2);
		assertEquals(b, b2);

		Object result = eval(op2).getResult();
		assertCVMEquals(expectedResult, result);
	}

	@Test
	public void testResultAndJuice() {
		Context<?> c = eval(source);

		Object result = c.getResult();
		assertCVMEquals(expectedResult, result);
	}
}
