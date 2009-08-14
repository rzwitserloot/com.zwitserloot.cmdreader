package com.zwitserloot.cmdreader;

public class InvalidCommandLineException extends Exception {
	private static final long serialVersionUID = 20080509L;
	
	public InvalidCommandLineException(String message) {
		super(message);
	}
}
