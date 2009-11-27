package com.zwitserloot.cmdreader;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;


public class TestCmdReader {
	private static class CmdArgs1 {
		@Shorthand("a")
		@Excludes("val2")
		@FullName("foo-bar")
		@Description("This is a description")
		@Parameterized
		private String val1;
		
		@Parameterized
		private String val2;
		
		@Parameterized
		@Shorthand("v")
		private String val3;
		
		@Shorthand("b")
		private boolean bool;
		
		@Requires("foo2")
		@Parameterized
		private String foo1;
		
		@Parameterized
		private String foo2;
		
		@Mandatory
		@Parameterized
		private String foo3;
	}
	
	private static class CmdArgs2 {
		@Shorthand("a")
		@Parameterized
		private int integer;
		
		@Shorthand("b")
		@Parameterized
		private double real;
		
		@Sequential
		@Parameterized
		private String val1;
		
		@Sequential
		@Parameterized
		private List<String> val2;
	}
	
	@SuppressWarnings("unused")
	private static class CmdArgs3 {
		@Parameterized
		@Mandatory(onlyIf="val2")
		private String val1;
		
		private boolean val2;
		
		@Parameterized
		@Mandatory(onlyIfNot="val4")
		private String val3;
		
		private boolean val4;
	}
	
	private CmdReader<CmdArgs1> reader1;
	private CmdReader<CmdArgs2> reader2;
	private CmdReader<CmdArgs3> reader3;
	
	@Before
	public void init() {
		reader1 = CmdReader.of(CmdArgs1.class);
		reader2 = CmdReader.of(CmdArgs2.class);
		reader3 = CmdReader.of(CmdArgs3.class);
	}
	
	@Test(expected=InvalidCommandLineException.class)
	public void testMandatory1() throws InvalidCommandLineException {
		reader1.make(new String[0]);
	}
	
	@Test
	public void testMandatory2() throws InvalidCommandLineException {
		reader3.make("--val3 foo");
		reader3.make("--val1 a --val2 --val3 foo");
		reader3.make("--val4");
	}
	
	@Test(expected=InvalidCommandLineException.class)
	public void testMandatory3() throws InvalidCommandLineException {
		reader3.make("");
	}
	
	@Test(expected=InvalidCommandLineException.class)
	public void testMandatory4() throws InvalidCommandLineException {
		reader3.make("--val2");
	}
	
	@Test(expected=InvalidCommandLineException.class)
	public void testRequires1() throws InvalidCommandLineException {
		reader1.make("--foo1 test --foo3=bar");
	}
	
	@Test
	public void testRequires2() throws InvalidCommandLineException {
		CmdArgs1 args = reader1.make("--foo1=test --foo2 blabla --foo3=bar");
		assertEquals("foo1 not set", "test", args.foo1);
		assertEquals("foo2 not set", "blabla", args.foo2);
		assertEquals("foo3 not set", "bar", args.foo3);
		assertFalse(args.bool);
		assertNull(args.val1);
		assertNull(args.val2);
		assertNull(args.val3);
	}
	
	@Test
	public void testExcludes1() throws InvalidCommandLineException {
		CmdArgs1 args = reader1.make("--foo-bar test1 -vb test2 --foo3=bar");
		assertEquals("foo3 not set", "bar", args.foo3);
		assertNull(args.foo1);
		assertNull(args.foo2);
		assertEquals("val3 not set", "test2", args.val3);
		assertEquals("val1 not set", "test1", args.val1);
		assertTrue(args.bool);
		assertNull(args.val2);
	}
	
	@Test(expected=InvalidCommandLineException.class)
	public void testExcludes2() throws InvalidCommandLineException {
		reader1.make("--foo-bar test1 -b --val2 bla --foo3=bar");
	}
	
	@Test(expected=InvalidCommandLineException.class)
	public void testBadCommandLine1() throws InvalidCommandLineException {
		reader1.make("--foo-bar");
	}
	
	@Test(expected=InvalidCommandLineException.class)
	public void testBadCommandLine2() throws InvalidCommandLineException {
		reader1.make("-abv test1");
	}
	
	@Test(expected=InvalidCommandLineException.class)
	public void testBadCommandLine3() throws InvalidCommandLineException {
		reader1.make("-abv test1 test2 test3");
	}
	
	@Test
	public void testSequential1() throws InvalidCommandLineException {
		CmdArgs2 args = reader2.make("foo bar baz");
		assertEquals("foo", args.val1);
		assertEquals(Arrays.asList("bar", "baz"), args.val2);
	}
	
	@Test
	public void testSequential2() throws InvalidCommandLineException {
		CmdArgs2 args = reader2.make("foo");
		assertEquals("foo", args.val1);
		assertNull(args.val2);
	}
	
	@Test
	public void testNumeric1() throws InvalidCommandLineException {
		CmdArgs2 args = reader2.make("-ab 12 13.5");
		assertEquals(12, args.integer);
		assertEquals(13.5, args.real, 0.000001);
	}
	
	@Test(expected=NumberFormatException.class)
	public void testNumeric2() throws InvalidCommandLineException {
		reader2.make("-a 12.5");
	}
	
	public void testSequential3() throws InvalidCommandLineException {
		CmdArgs2 args = reader2.make("--integer 10");
		assertEquals(10, args.integer);
		assertNull(args.val1);
		assertNull(args.val2);
	}
	
	public void testSequential4() throws InvalidCommandLineException {
		CmdArgs2 args = reader2.make("--val2 test1 --val2=test2");
		assertEquals(0, args.integer);
		assertNull(args.val1);
		assertEquals(Arrays.asList("test1", "test2"), args.val2);
	}
}
