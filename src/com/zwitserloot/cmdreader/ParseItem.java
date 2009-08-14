package com.zwitserloot.cmdreader;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.AbstractSequentialList;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

class ParseItem {
	private final List<Class<?>> LEGAL_CLASSES = Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Integer.class, Long.class, Short.class, Byte.class, Float.class, Double.class, Boolean.class, Character.class,
			String.class, Enum.class
			));
	
	private final Field field;
	private final boolean isCollection;
	private final Class<?> type;
	private final String fullName;
	private final boolean isSeq;
	private final boolean isParameterized;
	private final boolean isMandatory;
	private final String shorthand;
	private final String description;
	private final List<String> excludes;
	private final List<String> mandatoryIf;
	private final List<String> mandatoryIfNot;
	private final List<String> requires;
	
	ParseItem(Field field) {
		this.field = field;
		field.setAccessible(true);
		
		Class<?> rawType;
		if ( Collection.class.isAssignableFrom(field.getType()) ) {
			isCollection = true;
			Type genericType = field.getGenericType();
			Type[] typeArgs = null;
			if ( genericType instanceof ParameterizedType )
				typeArgs = ((ParameterizedType)genericType).getActualTypeArguments();
			if ( typeArgs != null && typeArgs.length == 1 && typeArgs[0] instanceof Class<?> )
				rawType = (Class<?>)typeArgs[0];
			else throw new IllegalArgumentException(String.format(
					"Only primitives, Strings, Enums, and Collections of those are allowed (for type: %s)", field.getGenericType()));
		} else {
			isCollection = false;
			rawType = field.getType();
		}
		
		if ( rawType == int.class ) this.type = Integer.class;
		else if ( rawType == long.class ) this.type = Long.class;
		else if ( rawType == short.class ) this.type = Short.class;
		else if ( rawType == byte.class ) this.type = Byte.class;
		else if ( rawType == double.class ) this.type = Double.class;
		else if ( rawType == float.class ) this.type = Float.class;
		else if ( rawType == char.class ) this.type = Character.class;
		else if ( rawType == boolean.class ) this.type = Boolean.class;
		else this.type = rawType;
		
		if ( !LEGAL_CLASSES.contains(type) ) throw new IllegalArgumentException("Not a valid class for command line parsing: " + field.getGenericType());
		
		this.fullName = setupFullName(field);
		this.isSeq = field.getAnnotation(Sequential.class) != null;
		this.isParameterized = field.getAnnotation(Parameterized.class) != null;
		this.shorthand = setupShorthand(field);
		this.description = setupDescription(field);
		this.isMandatory = setupMandatory(field);
		this.mandatoryIf = setupMandatoryIf(field);
		this.mandatoryIfNot = setupMandatoryIfNot(field);
		this.requires = setupRequires(field);
		this.excludes = setupExcludes(field);
		
		try {
			sanityChecks();
		} catch ( IllegalArgumentException e ) {
			throw new IllegalArgumentException(String.format("%s (at %s)", e.getMessage(), fullName));
		}
	}
	
	private void sanityChecks() {
		if ( !isParameterized && Boolean.class != type ) throw new IllegalArgumentException("Non-parameterized parameters must have type boolean. - it's there (true), or not (false).");
		if ( !isParameterized && isMandatory ) throw new IllegalArgumentException("Non-parameterized parameters must not be mandatory - what's the point of having it?");
		if ( isSeq && !"".equals(shorthand) ) throw new IllegalArgumentException("sequential parameters must not have any shorthands.");
		if ( isSeq && !isParameterized ) throw new IllegalArgumentException("sequential parameters must always be parameterized.");
	}
	
	static void multiSanityChecks(List<ParseItem> items) {
		int len = items.size();
		
		// No two ParseItems must have the same full name.
		for ( int i = 0 ; i < len ; i++ ) for ( int j = i+1 ; j < len ; j++ )
			if ( items.get(i).fullName.equalsIgnoreCase(items.get(j).fullName) )
				throw new IllegalArgumentException(String.format(
						"Duplicate full names for fields %s and %s.",
						items.get(i).field.getName(), items.get(j).field.getName()));
		
		// An isCollection, isSeq must be the last isSeq in the set.
		ParseItem isCollectionIsSeq = null;
		for ( ParseItem item : items ) {
			if ( item.isSeq && isCollectionIsSeq != null ) throw new IllegalArgumentException(String.format(
					"After the sequential, collection item %s no more sequential items allowed (at %s)",
					isCollectionIsSeq.fullName, item.fullName));
			if ( item.isSeq && item.isCollection ) isCollectionIsSeq = item;
		}
		
		// If the Xth isSeq is mandatory, every isSeq below X must also be mandatory.
		ParseItem firstNonMandatoryIsSeq = null;
		for ( ParseItem item : items ) {
			if ( !item.isSeq ) continue;
			if ( firstNonMandatoryIsSeq == null && !item.isMandatory ) firstNonMandatoryIsSeq = item;
			if ( item.isMandatory && firstNonMandatoryIsSeq != null ) throw new IllegalArgumentException(String.format(
					"Sequential item %s is non-mandatory, so %s which is a later sequential item must also be non-mandatory",
					firstNonMandatoryIsSeq.fullName, item.fullName));
		}
		
	}
	
	static Map<Character, ParseItem> makeShortHandMap(List<ParseItem> items) {
		Map<Character, ParseItem> out = new HashMap<Character, ParseItem>();
		
		for ( ParseItem item : items ) for ( char c : item.shorthand.toCharArray() ) {
			if ( out.containsKey(c) ) throw new IllegalArgumentException(String.format(
					"Both %s and %s contain the shorthand %s",
					out.get(c).fullName, item.fullName, c));
			else out.put(c, item);
		}
		
		return out;
	}
	
	String getFullName() {
		return fullName;
	}
	
	boolean isSeq() {
		return isSeq;
	}
	
	boolean isMandatory() {
		return isMandatory;
	}
	
	List<String> getMandatoryIf() {
		return mandatoryIf;
	}
	
	List<String> getMandatoryIfNot() {
		return mandatoryIfNot;
	}
	
	List<String> getRequires() {
		return requires;
	}
	
	List<String> getExcludes() {
		return excludes;
	}
	
	boolean isParameterized() {
		return isParameterized;
	}
	
	boolean isCollection() {
		return isCollection;
	}
	
	String getFullDescription() {
		return description;
	}
	
	private static final List<Class<?>> ARRAY_LIST_COMPATIBLES = Collections.unmodifiableList(
			Arrays.<Class<?>>asList(Collection.class, AbstractCollection.class, List.class, AbstractList.class, ArrayList.class));
	private static final List<Class<?>> HASH_SET_COMPATIBLES = Collections.unmodifiableList(
			Arrays.<Class<?>>asList(Set.class, AbstractSet.class, HashSet.class));
	private static final List<Class<?>> LINKED_LIST_COMPATIBLES = Collections.unmodifiableList(
			Arrays.<Class<?>>asList(AbstractSequentialList.class, Queue.class, LinkedList.class));
	
	@SuppressWarnings("unchecked")
	void set(Object o, String value) {
		Object v = stringToObject(value);
		
		try {
			if ( isCollection ) {
				Collection<Object> l = (Collection<Object>)field.get(o);
				if ( l == null ) {
					if ( ARRAY_LIST_COMPATIBLES.contains(field.getType()) ) l = new ArrayList<Object>();
					else if ( LINKED_LIST_COMPATIBLES.contains(field.getType()) ) l = new LinkedList<Object>();
					else if ( HASH_SET_COMPATIBLES.contains(field.getType()) ) l = new HashSet<Object>();
					else throw new IllegalArgumentException("Cannot construct a collection of type " + field.getType() + " -- try List, Set, Collection, or Queue.");
					field.set(o, l);
				}
				l.add(v);
			} else field.set(o, v);
		} catch ( IllegalAccessException e ) {
			throw new IllegalArgumentException("Huh?");
		}
	}
	
	@SuppressWarnings("unchecked")
	private Object stringToObject(String raw) {
		if ( String.class == type ) return raw;
		if ( Integer.class == type ) return Integer.parseInt(raw);
		if ( Long.class == type ) return Long.parseLong(raw);
		if ( Short.class == type ) return Short.parseShort(raw);
		if ( Byte.class == type ) return Byte.parseByte(raw);
		if ( Float.class == type ) return Float.parseFloat(raw);
		if ( Double.class == type ) return Double.parseDouble(raw);
		if ( Boolean.class == type ) return raw == null ? true : parseBoolean(raw);
		if ( Character.class == type ) return raw.length() == 0 ? (char)0 : raw.charAt(0);
		if ( Enum.class == type ) {
			return Enum.valueOf((Class<? extends Enum>)type, raw);
		}
		
		throw new IllegalArgumentException("Huh?");
	}
	
	private String setupFullName(Field field) {
		FullName ann = field.getAnnotation(FullName.class);
		if ( ann == null ) return field.getName();
		else {
			if ( ann.value().trim().equals("") ) throw new IllegalArgumentException("Missing name for field: " + field.getName());
			else return ann.value();
		}
	}
	
	private String setupShorthand(Field field) {
		Shorthand ann = field.getAnnotation(Shorthand.class);
		if ( ann == null ) return "";
		String[] value = ann.value();
		StringBuilder sb = new StringBuilder();
		for ( String v : value ) {
			char[] c = v.toCharArray();
			if ( c.length != 1 ) throw new IllegalArgumentException(String.format(
					"Shorthands must be strings of 1 character long. (%s at %s)", v, fullName));
			if ( c[0] == '-' ) throw new IllegalArgumentException(String.format(
					"The dash (-) is not a legal shorthand character. (at %s)", fullName));
			if ( sb.indexOf(v) > -1 ) throw new IllegalArgumentException(String.format(
					"Duplicate shorthand: %s (at %s)", v, fullName));
			sb.append(v);
		}
		
		return sb.toString();
	}
	
	private String setupDescription(Field field) {
		StringBuilder out = new StringBuilder();
		Description ann = field.getAnnotation(Description.class);
		
		if ( ann != null ) out.append(ann.value());
		if ( isCollection ) out.append(out.length() > 0 ? "  " : "").append("This option may be used multiple times.");
		if ( isParameterized && type != String.class ) {
			if ( out.length() > 0 ) out.append("  ");
			if ( type == Float.class || type == Double.class ) out.append("value is a floating point number.");
			if ( type == Integer.class || type == Long.class || type == Short.class || type == Byte.class )
				out.append("value is an integer.");
			if ( type == Boolean.class ) out.append("value is 'true' or 'false'.");
			if ( type == Character.class ) out.append("Value is a single character.");
			if ( type == Enum.class ) {
				out.append("value is one of: ");
				boolean first = true;
				
				Enum<?>[] enumConstants = (Enum<?>[])type.getEnumConstants();
				for ( Enum<?> e : enumConstants ) {
					if ( first ) first = false;
					else out.append(", ");
					out.append(e.name());
				}
				out.append(".");
			}
		}
		
		return out.toString();
	}
	
	private boolean setupMandatory(Field field) {
		Mandatory mandatory = field.getAnnotation(Mandatory.class);
		return mandatory != null && (mandatory.onlyIf().length == 0 && mandatory.onlyIfNot().length == 0);
	}
	
	private List<String> setupMandatoryIf(Field field) {
		Mandatory mandatory = field.getAnnotation(Mandatory.class);
		if ( mandatory == null || mandatory.onlyIf().length == 0 ) return Collections.emptyList();
		return Collections.unmodifiableList(Arrays.asList(mandatory.onlyIf()));
	}
	
	private List<String> setupMandatoryIfNot(Field field) {
		Mandatory mandatory = field.getAnnotation(Mandatory.class);
		if ( mandatory == null || mandatory.onlyIfNot().length == 0 ) return Collections.emptyList();
		return Collections.unmodifiableList(Arrays.asList(mandatory.onlyIfNot()));
	}
	
	private List<String> setupRequires(Field feild) {
		Requires requires = field.getAnnotation(Requires.class);
		if ( requires == null || requires.value().length == 0 ) return Collections.emptyList();
		return Collections.unmodifiableList(Arrays.asList(requires.value()));
	}
	
	private List<String> setupExcludes(Field field) {
		Excludes excludes = field.getAnnotation(Excludes.class);
		if ( excludes == null || excludes.value().length == 0 ) return Collections.emptyList();
		return Collections.unmodifiableList(Arrays.asList(excludes.value()));
	}
	
	private List<String> TRUE_VALS = Collections.unmodifiableList(Arrays.asList("1", "true", "t", "y", "yes", "on"));
	private List<String> FALSE_VALS = Collections.unmodifiableList(Arrays.asList("0", "false", "f", "n", "no", "off"));
	
	private boolean parseBoolean(String raw) {
		for ( String x : TRUE_VALS ) if ( x.equalsIgnoreCase(raw) ) return true;
		for ( String x : FALSE_VALS ) if ( x.equalsIgnoreCase(raw) ) return false;
		throw new IllegalArgumentException("Not a boolean: " + raw);
	}
	
	String getShorthand() {
		return shorthand;
	}
}