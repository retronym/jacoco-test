package jacocodemo

import org.jacoco.core.internal.analysis.filter.{ExposedAbstractMatcher, IFilter, IFilterContext, IFilterOutput}
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.{AbstractInsnNode, LineNumberNode, MethodNode, VarInsnNode}
import org.objectweb.asm.util.{Textifier, TraceMethodVisitor}

import java.io.{PrintWriter, StringWriter}

class ScalaFilter extends IFilter {
  override def filter(methodNode: MethodNode, context: IFilterContext, output: IFilterOutput): Unit = {
    val matcher = new ThrowNewMatchErrorMatcher()
    methodNode.instructions.forEach(i => matcher.apply(i, output))
    for (insn <- methodNode.instructions.toArray) {
      matcher(insn, output)
    }
    val debug = false
    if (debug) {
      val cls: String = textify(methodNode)
      println(cls)
    }

    if (methodNode.name == "<init>") {
      val outerNullMatcher = new OuterNullCheckMatcher()
      outerNullMatcher.apply(methodNode.instructions.getFirst, output)
    }
    if (methodNode.name == "applyOrElse" &&
      (methodNode.access & Opcodes.ACC_BRIDGE) == 0 && context.getSuperClassName.startsWith("scala/runtime/AbstractPartialFunction")) {
      val applyOrElseSuffixMatcher = new ApplyOrElseSuffixMatcher()
      applyOrElseSuffixMatcher.apply(methodNode.instructions.getFirst, methodNode.instructions.getLast, output)
    }
  }

  private class OuterNullCheckMatcher extends ExposedAbstractMatcher {
    def apply(start: AbstractInsnNode, output: IFilterOutput): Unit = {
      setCursor(start)
      nextIs(Opcodes.ALOAD)
      nextIs(Opcodes.IFNONNULL)
      nextIs(Opcodes.ACONST_NULL)
      nextIs(Opcodes.ATHROW)
      output.ignore(start, cursor)
    }
  }

  private class ThrowNewMatchErrorMatcher extends ExposedAbstractMatcher {
    def apply(start: AbstractInsnNode, output: IFilterOutput): Unit = {
      setCursor(start)
      nextIs(Opcodes.NEW)
      nextIs(Opcodes.DUP)
      nextIs(Opcodes.ALOAD)

      // e.g. by changing the compiler to call a factory method instead of the constructor directly.
      nextIsInvoke(Opcodes.INVOKESPECIAL, "scala/MatchError", "<init>", "(Ljava/lang/Object;)V")
      nextIs(Opcodes.ATHROW)
      if (cursor == null) return

      output.ignore(start, cursor)
    }
  }
  /**
   * This instruction suffix for the default case of a pattern matching partial function
   * uses exceptions for control flow in where `applyOrElse` is called from
   * `def apply(x: T1) = applyOrElse(x, PartialFunction.empty)`.
   *
   * {{{
   * ALOAD 2
   * ILOAD 1
   * INVOKESTATIC scala/runtime/BoxesRunTime.boxToInteger (I)Ljava/lang/Integer;
   * INVOKEINTERFACE scala/Function1.apply (Ljava/lang/Object;)Ljava/lang/Object; (itf)
   * ARETURN
   * L0
   * }}}
   */
  private class ApplyOrElseSuffixMatcher extends ExposedAbstractMatcher {
    def apply(start: AbstractInsnNode, end: AbstractInsnNode, output: IFilterOutput): Unit = {
      setCursor(start)
      while (cursor() != null) {
        cursor() match {
          case v: VarInsnNode if v.`var` == 2 =>
            output.ignore(cursor(), end)
            return
          case _ =>
        }
        next()
      }
    }
  }

  private def textify(methodNode: MethodNode) = {
    val trace = new TraceMethodVisitor(new Textifier)
    methodNode.accept(trace)

    val sw = new StringWriter
    val pw = new PrintWriter(sw)
    trace.p.print(pw)
    val cls = sw.toString.trim
    cls
  }
}
