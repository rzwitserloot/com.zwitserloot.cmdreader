/*
 * Copyright © 2010 Reinier Zwitserloot.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.zwitserloot.cmdreader;

import java.util.Arrays;
import java.util.Collections;
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
		private String val1;
		
		private String val2;
		
		@Shorthand("v")
		private String val3;
		
		@Shorthand("b")
		private boolean bool;
		
		@Requires("foo2")
		private String foo1;
		
		private String foo2;
		
		@Mandatory
		private String foo3;
	}
	
	private static class CmdArgs2 {
		@Shorthand("a")
		private int integer;
		
		@Shorthand("b")
		private double real;
		
		@Sequential(1)
		private String val1;
		
		@Sequential(2)
		private List<String> val2;
	}
	
	@SuppressWarnings("unused")
	private static class CmdArgs3 {
		@Mandatory(onlyIf="val2")
		private String val1;
		
		private boolean val2;
		
		@Mandatory(onlyIfNot="val4")
		private String val3;
		
		private boolean val4;
	}
	
	private static class CmdArgs4 {
		@ExcludesGroup
		private boolean bar1;
		
		@ExcludesGroup
		private boolean bar2;
		
		@ExcludesGroup({"default", "foobar"})
		private boolean bar3;
		
		@ExcludesGroup("foobar")
		private boolean bar4;
		
		@ExcludesGroup("foobar")
		private boolean bar5;
	}
	
	@SuppressWarnings("all")
	private static class CmdArgsSeqFail1 {
		@Sequential(1)
		String foo;
		
		@Mandatory
		@Sequential(2)
		String bar;
	}
	
	@SuppressWarnings("all")
	private static class CmdArgsSeqFail2 {
		@Mandatory
		@Sequential(1)
		List<String> col;
		
		@Mandatory
		@Sequential(2)
		String bar;
		
		@Sequential(3)
		String baz;
	}
	
	private static class CmdArgsSeq1 {
		@Mandatory
		@Sequential(1)
		String arg1;
		
		@Sequential(2)
		List<String> list;
		
		@Mandatory
		@Sequential(3)
		String arg2;
		
		@Mandatory
		@Sequential(4)
		String arg3;
	}
	
	private static class CmdArgsSeq2 {
		@Mandatory
		@Sequential(1)
		String arg1;
		
		@Mandatory
		@Sequential(2)
		List<String> list;
	}
	
	private CmdReader<CmdArgs1> reader1;
	private CmdReader<CmdArgs2> reader2;
	private CmdReader<CmdArgs3> reader3;
	private CmdReader<CmdArgs4> reader4;
	private CmdReader<CmdArgsSeq1> readerSeq1;
	private CmdReader<CmdArgsSeq2> readerSeq2;
	
	@Before
	public void init() {
		reader1 = CmdReader.of(CmdArgs1.class);
		reader2 = CmdReader.of(CmdArgs2.class);
		reader3 = CmdReader.of(CmdArgs3.class);
		reader4 = CmdReader.of(CmdArgs4.class);
		readerSeq1 = CmdReader.of(CmdArgsSeq1.class);
		readerSeq2 = CmdReader.of(CmdArgsSeq2.class);
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
	public void testExcludesGroup1() throws InvalidCommandLineException {
		reader4.make("--bar1 --bar3");
	}
	
	@Test
	public void testExcludesGroup2() throws InvalidCommandLineException {
		reader4.make("--bar1 --bar4");
	}
	
	@Test(expected=InvalidCommandLineException.class)
	public void testExcludesGroup3() throws InvalidCommandLineException {
		reader4.make("--bar3 --bar4 --bar5");
	}
	
	@Test(expected=InvalidCommandLineException.class)
	public void testExcludesGroup4() throws InvalidCommandLineException {
		reader4.make("--bar3 --bar5");
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
	
	@Test(expected=IllegalArgumentException.class)
	public void testSeqFail1() throws IllegalArgumentException {
		CmdReader.of(CmdArgsSeqFail1.class);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testSeqFail2() throws IllegalArgumentException {
		CmdReader.of(CmdArgsSeqFail2.class);
	}
	
	public void testSequential5() throws InvalidCommandLineException {
		CmdArgsSeq1 args = readerSeq1.make("foo1 foo2 foo3 foo4 foo5 foo6 foo7");
		assertEquals("foo1", args.arg1);
		assertEquals(Arrays.asList("foo2", "foo3", "foo4", "foo5"), args.list);
		assertEquals("foo6", args.arg2);
		assertEquals("foo7", args.arg3);
		
		args = readerSeq1.make("foo1 foo2 foo3");
		assertEquals("foo1", args.arg1);
		assertEquals(Collections.emptyList(), args.list);
		assertEquals("foo2", args.arg2);
		assertEquals("foo3", args.arg3);
		
		CmdArgsSeq2 args2 = readerSeq2.make("foo1 foo2 foo3 foo4 foo5 foo6 foo7");
		assertEquals("foo1", args2.arg1);
		assertEquals(Arrays.asList("foo2", "foo3", "foo4", "foo5", "foo6", "foo7"), args2.list);
	}
	
	@Test(expected=InvalidCommandLineException.class)
	public void testSequential6() throws InvalidCommandLineException {
		readerSeq2.make("foo1");
	}
}
