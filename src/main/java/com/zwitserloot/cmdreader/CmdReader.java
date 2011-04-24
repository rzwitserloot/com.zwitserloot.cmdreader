/*
 * Copyright © 2010-2011 Reinier Zwitserloot.
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Parses command line arguments.
 * 
 * The CmdReader can turn strings like<br>
 * <code>-fmv /foo/bar /bar/baz --color=white *.xyzzy *.cheese</code><br>
 * into something that easier to work with programatically.
 * <p>
 * To use it, first create a 'descriptor' class; this is a class that contains just fields and no code.
 * Each field represents a command line option. The type of each field can be any primitive or primitive wrapper, or String,
 * or arrays / lists / sets of such types.
 * 
 * Annotate each field with the various annotations in the cmdreader package
 * to influence its behaviour. A short description of all annotations (they are all optional):
 * <dl>
 * <dt>FullName
 * <dd>(defaults to field name). The name of the option. Used in the Excludes and Mandatory annotations,
 *  and also allowed on the command line itself as --fullname(=value)
 * <dt>Shorthand
 * <dd>Instead of --fullname, -shorthand is also allowed. You can have as many shorthands as you want. Usually single characters.
 * <dt>Description
 * <dd>A human readable description of the field. Used to auto-generate command line help.
 * <dt>Excludes
 * <dd>A list of names (see FullName) that cannot co-exist together with this option. If this option is present
 *  as well as one of the excluded ones, an error will be generated.
 * <dt>ExcludesGroup
 * <dd>A list of keywords. An error is generated if two or more options that share an <code>@ExcludesGroup</code> keyword are present.
 * This feature is useful for selecting various mutually exclusive modes of operation, such as 'pack, unpack, test' for a compression tool.
 * <dt>Mandatory
 * <dd>Indicates that the option must be present. You may optionally specify 'onlyIf' and 'onlyIfNot', which are lists of
 *  names (see FullName). onlyIf means: This is mandatory only if at least one of these options is present. onlyIfNot means:
 *  I am not optional only if one of these options is present, otherwise I am optional. On fields of a collection type, this means at least
 *  one such option must be present.
 *  <dt>Sequential
 *  <dd>Use me if there is no option name. In other words, those command line options that do not 'belong' to a -something,
 *  go here. You can have as many as you like, but only one can be a collection type. It is an error if a Sequential
 *  annotated field is of type boolean. If you have multiple {@code @Sequential} options, you need to specify the ordering.
 *  </dl>
 *  
 *  Fields that do not show up in the command line arguments aren't modified, so if you want a default, just set the field in the descriptor class.
 */
public class CmdReader<T> {
	private final Class<T> settingsDescriptor;
	private final List<ParseItem> items;
	private final Map<Character, ParseItem> shorthands;
	private final List<ParseItem> seqList;
	private final int collectionSeqIndex;
	
	private CmdReader(Class<T> settingsDescriptor) {
		this.settingsDescriptor = settingsDescriptor;
		this.items = Collections.unmodifiableList(init());
		this.shorthands = ParseItem.makeShortHandMap(this.items);
		this.seqList = makeSeqList(this.items);
		int cSI = -1;
		for (int i = 0; i < seqList.size(); i++) if (seqList.get(i).isCollection()) cSI = i;
		this.collectionSeqIndex = cSI;
	}
	
	/**
	 * Create a new CmdReader with the specified class as the template that defines what each option means.
	 * 
	 * See the class javadoc for more information on how to make these classes.
	 * 
	 * @param settingsDescriptor Class with fields which will be filled with the command line variables.
	 * @throws IllegalArgumentException If the <em>settingsDescriptor</em> contains flaws, for example
	 *   if two different fields both use the same 'full name'. See the class javadoc for a more complete list of causes.
	 */
	public static <T> CmdReader<T> of(Class<T> settingsDescriptor) {
		return new CmdReader<T>(settingsDescriptor);
	}
	
	private List<ParseItem> init() {
		Class<?> c = settingsDescriptor;
		List<ParseItem> out = new ArrayList<ParseItem>();
		
		while (c != Object.class) {
			Field[] fields = settingsDescriptor.getDeclaredFields();
			for (Field field : fields) {
				field.setAccessible(true);
				if (Modifier.isStatic(field.getModifiers())) continue;
				out.add(new ParseItem(field));
			}
			c = c.getSuperclass();
		}
		
		ParseItem.multiSanityChecks(out);
		return out;
	}
	
	private static List<ParseItem> makeSeqList(List<ParseItem> items) {
		List<ParseItem> seqList = new ArrayList<ParseItem>();
		int lowest = Integer.MAX_VALUE;
		for (ParseItem  item : items) if (item.isSeq()) lowest = Math.min(item.getSeqOrder(), lowest);
		
		while (true) {
			int nextLowest = lowest;
			for (ParseItem item : items) if (item.isSeq() && item.getSeqOrder() >= lowest) {
				if (item.getSeqOrder() == lowest) seqList.add(item);
				else if (nextLowest == lowest) nextLowest = item.getSeqOrder();
				else if (item.getSeqOrder() < nextLowest) nextLowest = item.getSeqOrder();
			}
			if (nextLowest == lowest) break;
			lowest = nextLowest;
		}
		
		ParseItem.multiSeqSanityChecks(seqList);
		
		return seqList;
	}
	
	private static final int SCREEN_WIDTH = 72;
	
	/**
	 * Generates an extensive string explaining the command line options.
	 * You should print this if the make() method throws an InvalidCommandLineException, for example.
	 * The detail lies between your average GNU manpage and your average GNU command's output if you specify --help.
	 * 
	 * Is automatically wordwrapped at standard screen width (72 characters). Specific help for each option is mostly
	 * gleaned from the {@link com.zwitserloot.cmdreader.Description} annotations.
	 * 
	 * @param commandName used to prefix the example usages.
	 */
	public String generateCommandLineHelp(String commandName) {
		StringBuilder out = new StringBuilder();
		
		int maxFullName = 0;
		int maxShorthand = 0;
		
		for (ParseItem item : items) {
			if (item.isSeq()) continue;
			maxFullName = Math.max(maxFullName, item.getFullName().length() + (item.isParameterized() ? 4 : 0));
			maxShorthand = Math.max(maxShorthand, item.getShorthand().length());
		}
		
		if (maxShorthand == 0) maxShorthand++;
		
		maxShorthand = maxShorthand * 3 -1;
		
		generateShortSummary(commandName, out);
		generateSequentialArgsHelp(out);
		generateMandatoryArgsHelp(maxFullName, maxShorthand, out);
		generateOptionalArgsHelp(maxFullName, maxShorthand, out);
		return out.toString();
	}
	
	private void generateShortSummary(String commandName, StringBuilder out) {
		if (commandName != null && commandName.length() > 0) out.append(commandName).append(" ");
		
		StringBuilder sb = new StringBuilder();
		for (ParseItem item : items) if (!item.isSeq() && !item.isMandatory()) sb.append(item.getShorthand());
		if (sb.length() > 0) {
			out.append("[-").append(sb).append("] ");
			sb.setLength(0);
		}
		
		for (ParseItem item : items) if (!item.isSeq() && item.isMandatory()) sb.append(item.getShorthand());
		if (sb.length() > 0) {
			out.append("-").append(sb).append(" ");
			sb.setLength(0);
		}
		
		for (ParseItem item : items) if (!item.isSeq() && item.isMandatory() && item.getShorthand().length() == 0) {
			out.append("--").append(item.getFullName()).append("=val ");
		}
		
		for (ParseItem item : items) if (item.isSeq()) {
			if (!item.isMandatory()) out.append('[');
			out.append(item.getFullName());
			if (!item.isMandatory()) out.append(']');
			out.append(' ');
		}
		out.append("\n");
	}
	
	private void generateSequentialArgsHelp(StringBuilder out) {
		List<ParseItem> items = new ArrayList<ParseItem>();
		
		for (ParseItem item : this.seqList) if (item.getFullDescription().length() > 0) items.add(item);
		if (items.size() == 0) return;
		
		int maxSeqArg = 0;
		for (ParseItem item : items) maxSeqArg = Math.max(maxSeqArg, item.getFullName().length());
		
		out.append("\n  Sequential arguments:\n");
		for (ParseItem item : items) generateSequentialArgHelp(maxSeqArg, item, out);
	}
	
	private void generateMandatoryArgsHelp(int maxFullName, int maxShorthand, StringBuilder out) {
		List<ParseItem> items = new ArrayList<ParseItem>();
		for (ParseItem item : this.items) if (item.isMandatory() && !item.isSeq()) items.add(item);
		
		if (items.size() == 0) return;
		
		out.append("\n  Mandatory arguments:\n");
		for (ParseItem item : items) generateArgHelp(maxFullName, maxShorthand, item, out);
	}
	
	private void generateOptionalArgsHelp(int maxFullName, int maxShorthand, StringBuilder out) {
		List<ParseItem> items = new ArrayList<ParseItem>();
		for (ParseItem item : this.items) if (!item.isMandatory() && !item.isSeq()) items.add(item);
		
		if (items.size() == 0) return;
		
		out.append("\n  Optional arguments:\n");
		for (ParseItem item : items) generateArgHelp(maxFullName, maxShorthand, item, out);
	}
	
	private void generateArgHelp(int maxFullName, int maxShorthand, ParseItem item, StringBuilder out) {
		out.append("    ");
		String fn = item.getFullName() + (item.isParameterized() ? "=val" : "");
		out.append(String.format("--%-" + maxFullName + "s ", fn));
		
		StringBuilder sh = new StringBuilder();
		for (char c : item.getShorthand().toCharArray()) {
			if (sh.length() > 0) sh.append(" ");
			sh.append("-").append(c);
		}
		
		out.append(String.format("%-" + maxShorthand + "s ", sh));
		
		int left = SCREEN_WIDTH - 8 - maxShorthand - maxFullName;
		
		String description = item.getFullDescription();
		if (description.length() == 0 || description.length() < left) {
			out.append(description).append("\n");
			return;
		}
		
		for (String line : wordbreak(item.getFullDescription(), SCREEN_WIDTH -8)) {
			out.append("\n        ").append(line);
		}
		out.append("\n");
	}
	
	private void generateSequentialArgHelp(int maxSeqArg, ParseItem item, StringBuilder out) {
		out.append("    ");
		out.append(String.format("%-" + maxSeqArg + "s   ", item.getFullName()));
		
		int left = SCREEN_WIDTH - 7 - maxSeqArg;
		
		String description = item.getFullDescription();
		if (description.length() == 0 || description.length() < left) {
			out.append(description).append("\n");
			return;
		}
		
		for (String line : wordbreak(item.getFullDescription(), SCREEN_WIDTH - 8)) {
			out.append("\n        ").append(line);
		}
		out.append("\n");
	}
	
	private static List<String> wordbreak(String text, int width) {
		StringBuilder line = new StringBuilder();
		List<String> out = new ArrayList<String>();
		int lastSpace = -1;
		
		for (char c : text.toCharArray()) {
			if (c == '\t') c = ' ';
			
			if (c == '\n') {
				out.add(line.toString());
				line.setLength(0);
				lastSpace = -1;
				continue;
			}
			
			if (c == ' ') {
				lastSpace = line.length();
				line.append(' ');
			} else line.append(c);
			
			if (line.length() > width && lastSpace > 8) {
				out.add(line.substring(0, lastSpace));
				String left = line.substring(lastSpace+1);
				line.setLength(0);
				line.append(left);
				lastSpace = -1;
			}
		}
		
		if (line.length() > 0) out.add(line.toString());
		
		return out;
	}
	
	/**
	 * Parses the provided command line into an instance of your command line descriptor class.
	 * 
	 * The string is split on spaces. However, quote symbols can be used to prevent this behaviour. To write literal quotes,
	 * use the \ escape. a double \\ is seen as a single backslash.
	 * 
	 * @param in A command line string, such as -frl "foo bar"
	 * @throws InvalidCommandLineException If the user entered a wrong command line.
	 *           The exception's message is english and human readable - print it back to the user!
	 * @throws IllegalArgumentException If your descriptor class has a bug in it. A list of causes can be found in the class javadoc.
	 */
	public T make(String in) throws InvalidCommandLineException, IllegalArgumentException {
		List<String> out = new ArrayList<String>();
		StringBuilder sb = new StringBuilder();
		boolean inQuote = false;
		boolean inBack = false;
		
		for (char c : in.toCharArray()) {
			if (inBack) {
				inBack = false;
				if (c == '\n') continue;
				sb.append(c);
			}
			
			if (c == '\\') {
				inBack = true;
				continue;
			}
			
			if (c == '"') {
				inQuote = !inQuote;
				continue;
			}
			
			if (c == ' ' && !inQuote) {
				String p = sb.toString();
				sb.setLength(0);
				if (p.equals("")) continue;
				out.add(p);
				continue;
			}
			sb.append(c);
		}
		
		if (sb.length() > 0) out.add(sb.toString());
		
		return make(out.toArray(new String[out.size()]));
	}
	
	/**
	 * Parses the provided command line into an instance of your command line descriptor class.
	 * 
	 * Each part of the string array is taken literally as a single argument; quotes are not parsed and things are not split
	 * on spaces. Normally the shell does this for you.
	 * 
	 * If you want to parse the String[] passed to java <code>main</code> methods, use this method.
	 * 
	 * @param in A command line string chopped into pieces, such as ["-frl", "foo bar"].
	 * @throws InvalidCommandLineException If the user entered a wrong command line.
	 *           The exception's message is english and human readable - print it back to the user!
	 * @throws IllegalArgumentException If your descriptor class has a bug in it. A list of causes can be found in the class javadoc.
	 */
	public T make(String[] in) throws InvalidCommandLineException {
		final T obj = construct();
		
		if (in == null) in = new String[0];
		
		class State {
			List<ParseItem> used = new ArrayList<ParseItem>();
			
			void handle(ParseItem item, String value) {
				item.set(obj, value);
				used.add(item);
			}
			
			void finish() throws InvalidCommandLineException {
				checkForGlobalMandatories();
				checkForExcludes();
				checkForGroupExcludes();
				checkForRequires();
				checkForMandatoriesIf();
				checkForMandatoriesIfNot();
			}
			
			private void checkForGlobalMandatories() throws InvalidCommandLineException {
				for (ParseItem item : items) if (item.isMandatory() && !used.contains(item))
					throw new InvalidCommandLineException(
						"You did not specify mandatory parameter " + item.getFullName());
			}
			
			private void checkForExcludes() throws InvalidCommandLineException {
				for (ParseItem item : items) if (used.contains(item)) {
					for (String n : item.getExcludes()) {
						for (ParseItem i : items) if (i.getFullName().equals(n) && used.contains(i))
							throw new InvalidCommandLineException(
									"You specified parameter " + i.getFullName() +
									" which cannot be used together with " + item.getFullName());
					}
				}
			}
			
			private void checkForGroupExcludes() throws InvalidCommandLineException {
				for (ParseItem item : items) if (used.contains(item)) {
					for (String n : item.getExcludesGroup()) {
						for (ParseItem i : items) {
							if (i == item || !used.contains(i)) continue;
							if (i.getExcludesGroup().contains(n)) {
								throw new InvalidCommandLineException(
										"You specified parameter " + i.getFullName() +
										" which cannot be used together with " + item.getFullName());
							}
						}
					}
				}
			}
			
			private void checkForRequires() throws InvalidCommandLineException {
				for (ParseItem item : items) if (used.contains(item)) {
					for (String n : item.getRequires()) {
						for (ParseItem i : items) if (i.getFullName().equals(n) && !used.contains(i))
							throw new InvalidCommandLineException(
									"You specified parameter " + item.getFullName() +
									" which requires that you also supply " + i.getFullName());
					}
				}
			}
			
			private void checkForMandatoriesIf() throws InvalidCommandLineException {
				for (ParseItem item : items) {
					if (used.contains(item) || item.getMandatoryIf().size() == 0) continue;
					for (String n : item.getMandatoryIf()) {
						for (ParseItem i : items) if (i.getFullName().equals(n) && used.contains(i))
							throw new InvalidCommandLineException(
									"You did not specify parameter " + item.getFullName() +
									" which is mandatory if you use " + i.getFullName());
					}
				}
			}
			
			private void checkForMandatoriesIfNot() throws InvalidCommandLineException {
				nextItem:
				for (ParseItem item : items) {
					if (used.contains(item) || item.getMandatoryIfNot().size() == 0) continue;
					for (String n : item.getMandatoryIfNot()) {
						for (ParseItem i : items) if (i.getFullName().equals(n) && used.contains(i))
							continue nextItem;
					}
					
					StringBuilder alternatives = new StringBuilder();
					if (item.getMandatoryIfNot().size() > 1) alternatives.append("one of ");
					for (String n : item.getMandatoryIfNot()) alternatives.append(n).append(", ");
					alternatives.setLength(alternatives.length() - 2);
					
					throw new InvalidCommandLineException(
							"You did not specify parameter " + item.getFullName() +
							" which is mandatory unless you use " + alternatives);
				}
			}
		}
		
		State state = new State();
		List<String> seqArgs = new ArrayList<String>();
		
		for (int i = 0; i < in.length; i++) {
			if (in[i].startsWith("--")) {
				int idx = in[i].indexOf('=');
				String key = idx == -1 ? in[i].substring(2) : in[i].substring(2, idx);
				String value = idx == -1 ? "" : in[i].substring(idx+1);
				if (value.length() == 0 && idx != -1) throw new InvalidCommandLineException(
						"invalid command line argument - you should write something after the '=': " + in[i]);
				boolean handled = false;
				for (ParseItem item : items) if (item.getFullName().equalsIgnoreCase(key)) {
					if (item.isParameterized() && value.length() == 0) {
						if (i < in.length - 1 && !in[i+1].startsWith("-")) value = in[++i];
						else throw new InvalidCommandLineException(String.format(
								"invalid command line argument - %s requires a parameter but there is none.", key));
					}
					state.handle(item, !item.isParameterized() && value.length() == 0 ? null : value);
					handled = true;
					break;
				}
				if (!handled) throw new InvalidCommandLineException(
						"invalid command line argument - I don't know about that option: " + in[i]);
			} else if (in[i].startsWith("-")) {
				for (char c : in[i].substring(1).toCharArray()) {
					ParseItem item = shorthands.get(c);
					if (item == null) throw new InvalidCommandLineException(String.format(
							"invalid command line argument - %s is not a known option: %s", c, in[i]));
					if (item.isParameterized()) {
						String value;
						if (i < in.length - 1 && !in[i+1].startsWith("-")) value = in[++i];
						else throw new InvalidCommandLineException(String.format(
								"invalid command line argument - %s requires a parameter but there is none.", c));
						state.handle(item, value);
					} else state.handle(item, null);
				}
			} else {
				seqArgs.add(in[i]);
			}
		}
		
		if (collectionSeqIndex == -1 && seqArgs.size() > seqList.size()) {
			throw new InvalidCommandLineException(String.format(
					"invalid command line argument - you've provided too many free-standing arguments: %s", seqArgs.get(seqList.size())));
		}
		
		if (collectionSeqIndex == -1) {
			for (int i = 0; i < seqArgs.size(); i++) {
				ParseItem item = seqList.get(i);
				state.handle(item, seqArgs.get(i));
			}
		} else {
			int totalCollectionSize = seqArgs.size() - seqList.size() + 1;
			
			int argsIdx = 0;
			int optIdx = 0;
			int colIdx = 0;
			while (argsIdx < seqArgs.size()) {
				if (optIdx < collectionSeqIndex) {
					ParseItem item = seqList.get(optIdx);
					state.handle(item, seqArgs.get(argsIdx));
					optIdx++;
					argsIdx++;
				} else if (optIdx == collectionSeqIndex) {
					ParseItem item = seqList.get(optIdx);
					while (colIdx < totalCollectionSize) {
						state.handle(item, seqArgs.get(argsIdx));
						colIdx++;
						argsIdx++;
					}
					optIdx++;
				} else {
					ParseItem item = seqList.get(optIdx);
					state.handle(item, seqArgs.get(argsIdx));
					optIdx++;
					argsIdx++;
				}
			}
		}
		
		state.finish();
		
		return obj;
	}
	
	private T construct() {
		try {
			Constructor<T> constructor = settingsDescriptor.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		} catch (NoSuchMethodException e) {
			throw new IllegalArgumentException(String.format(
					"A CmdReader class must have a no-args constructor: %s", settingsDescriptor));
		} catch (InstantiationException e) {
			throw new IllegalArgumentException(String.format(
					"A CmdReader class must not be an interface or abstract: %s", settingsDescriptor));
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException("Huh?");
		} catch (InvocationTargetException e) {
			throw new IllegalArgumentException(
					"Exception occurred when constructing CmdReader class " + settingsDescriptor, e.getCause());
		}
	}
	
	/**
	 * Turns a list of strings, such as "Hello", "World!" into a single string, each element separated by a space.
	 * Use it if you want to grab the rest of the command line as a single string, spaces and all; include a @Sequential
	 * List of Strings and run squash on it to do this.
	 */
	public static String squash(Collection<String> collection) {
		Iterator<String> i = collection.iterator();
		StringBuilder out = new StringBuilder();
		
		while (i.hasNext()) {
			out.append(i.next());
			if (i.hasNext()) out.append(' ');
		}
		
		return out.toString();
	}
}
