# com.zwitserloot.cmdreader

## How to use

First, create a class to represent your command line options. Create one field for each command line option you want, then use the annotations of the `com.zwitserloot.cmdreader` package on the field to configure that option. Then, create a `CmdReader` instance and use it to parse a command line into an instance of your class. You can also use a `CmdReader` instance to generate comprehensive command line help.

For the full manual, you should visit the javadoc, which you can build yourself with `ant javadoc`, after which it'll be available in `doc/api/index.html`. For a quick intro, check out the example program at the end of this documentation.

### Example program

	import com.zwitserloot.cmdreader.*;
	
	public class Test {
		static class CmdArgs {
			@Shorthand("x")
			@Description("Excludes the given file.")
			java.util.List<String> exclude;
			
			@Sequential
			@Mandatory
			@Description("The directory to turn into a compressed archive.")
			String compress;
		}
		
		public static void main(String[] rawArgs) {
			CmdReader<CmdArgs> reader = CmdReader.of(CmdArgs.class);
			CmdArgs args;
			try {
				args = reader.make(rawArgs);
			} catch (InvalidCommandLineException e) {
				System.err.println(e.getMessage());
				System.err.println(reader.generateCommandLineHelp("java Test"));
				return;
			}
			
			System.err.println("If this was a real program, I would now compress " + args.compress);
			System.err.println("And I'd be excluding: " + args.exclude);
		}
	}

### All annotation options

#### @Description
Describes this option. This description is used by `CmdReader.generateCommandLineHelp` to generate the command line help message, and nowhere else.

#### @FullName
By default an option's full name is equal to its field name. If you want to override this, for example because you'd like your option to include a dash
which is not a legal java identifier character, you can do so here. Any option can be accessed using the "--fullname(=value)" syntax, and full names are also used
to refer to other options.

#### @Shorthand
A shorthand is a single character which can be used as a short alternative. For example:

	@Shorthand("h") boolean help;

Can be set to true either via `--help` or via `-h`. You can include more than one shorthand character if you want.
Note that multiple shorthands can be chained together if they are booleans, i.e. if 'x' and  'y' are booleans, and 'b' is a String::

	java -jar yourapp.jar -xyb valueForB

#### @Excludes
Tells CmdReader that this option cannot co-exist with the listed other options. For example:

	String foo;
	@Excludes("foo") boolean bar;

Means that an error will be generated when the user attempts to pass `--foo Hello --bar` on the command line.

Multiple options can be listed in one `@Excludes`.

#### @ExcludesGroup
List any number of unique group names as parameter to the `@ExcludesGroup` method. Any command line that lists more than one option that both share
an excludes group is treated as an invalid command line. This is useful if you have a number of 'modes' which are all mutually exclusive. For example, a
backup or unzip tool can various mutually exclusive modes:

	@Shorthand("c") @ExcludesGroup("mode") boolean createArchive;
	@Shorthand("x") @ExcludesGroup("mode") boolean extractArchive;
	@Shorthand("v") @ExcludesGroup("mode") boolean verifyArchive;

#### @Mandatory
Used to indicate an option is required. Optionally you can include the `onlyIf` or `onlyIfNot` parameter, listing any number of other options, to fine-tune
when this option is mandatory and when it isn't. `@Mandatory` on a field with a collection type implies at least one such option must be present.

#### @Sequential
Used to indicate that this option is the 'default' and that no switch is required on the command line for it. For example, if your command line tool works on one
file, then you can allow your app to be invoked as `java -jar yourapp.jar filename` if you add this annotation:

	@Mandatory @Sequential String fileName;

Multiple sequential arguments are allowed; exactly one such argument may be a collection type, and it does not have to be the last one. Example for a copy program:

	@Mandatory @Sequential(1) List<String> from;
	@Mandatory @Sequential(2) String to;

#### @Requires
Used to indicate that if this option is present, the listed option must also be present. Like `@Mandatory` but this one is put on the other option.

## How to compile / develop

run:

	ant

and that's that. All dependencies will be downloaded automatically. The jar file you need will be in the `dist` directory. If you want to work on the code, run 'ant eclipse' or 'ant intellij' to set up the project dir as an eclipse or intellij project.

