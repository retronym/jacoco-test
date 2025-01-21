package jacocodemo

import org.jacoco.core.internal.analysis.filter.{AbstractMatcher, ExposedAbstractMatcher, IFilter, IFilterContext, IFilterOutput}
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.{AbstractInsnNode, JumpInsnNode, LdcInsnNode, LineNumberNode, MethodNode, VarInsnNode}
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
      var c1 = cursor()
      nextIs(Opcodes.ALOAD)
      var line = -1
      while (c1 != null && line == -1) {
        if (c1.getType == AbstractInsnNode.LINE) {
          line = c1.asInstanceOf[LineNumberNode].line
        } else {
          c1 = c1.getPrevious
        }
      }

      // e.g. by changing the compiler to call a factory method instead of the constructor directly.
      nextIsInvoke(Opcodes.INVOKESPECIAL, "scala/MatchError", "<init>", "(Ljava/lang/Object;)V")
      nextIs(Opcodes.ATHROW)
      if (cursor == null) return

      output.ignore(start, cursor)
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
