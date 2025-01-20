package jacocodemo

import org.jacoco.core.internal.analysis.filter.{IFilter, IFilterContext, IFilterOutput}
import org.objectweb.asm.tree.MethodNode

class ScalaFilter extends  IFilter {
  override def filter(methodNode: MethodNode, context: IFilterContext, output: IFilterOutput): Unit = {
  }
}
