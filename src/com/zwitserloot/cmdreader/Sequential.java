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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * All options that are marked with {@code @Sequential} do not need an option name. Only one may be an array or list, and these options may not be of type {@code boolean}.
 * For example:
 * 
 * <pre>
 *     &#64;Mandatory &#64;Sequential(1) List&lt;String&gt; from;
 *     &#64;Mandatory &#64;Sequential(2) String to;
 * </pre>
 * 
 * could be part of the command line arguments structure for a copy command.
 */
@Retention(value=RetentionPolicy.RUNTIME)
@Target(value=ElementType.FIELD)
@Documented
public @interface Sequential {
	/** Because fields in classes do not have a defined order, if you have multiple arguments marked {@code @Sequential} you need to specify the order. */
	int value() default 0;
}
