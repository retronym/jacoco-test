# A better JaCoCo experience for Scala

Exploring ways to help filter methods / branches / instructions related to synthetic constructs created by the
Scala compiler.

There are two ways to achieve this:
 - Change the way the Scala compiler generates bytecode to make existing JaCoCo filters work
   - Add a `@Generated` annotation to synthetic methods (accessors, mixin forwarders, case class methods, etc.)
 - Add new JaCoCo filters to ignore synthetic constructs
 - A combination of both (add new filters, but also change the way the Scala compiler generates bytecode)

Compiler changes are initially being explored by adding a new compiler plugin. Deeper changes will require
modifications to the Scala compiler itself.
