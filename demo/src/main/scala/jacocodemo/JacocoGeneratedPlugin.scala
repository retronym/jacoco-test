package jacocodemo

import scala.reflect.internal.Flags
import scala.tools.nsc.Phase
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.transform.TypingTransformers

abstract class JacocoGeneratedPlugin extends Plugin {

  import global._

  (t: ExistentialType) => t.typeArgs

  override val description: String = "jacoco-generated"
  override val name: String = "jacoco-generated"

  override val components: List[PluginComponent] = List(middle, late)

  private def markAsGenerated(tree: Tree) = {
    tree.symbol.withAnnotations(List(AnnotationInfo(typeOf[Generated], Nil, Nil)))
  }

  private object middle extends PluginComponent with TypingTransformers {
    val global: JacocoGeneratedPlugin.this.global.type = JacocoGeneratedPlugin.this.global

    override def newPhase(prev: Phase): Phase = new StdPhase(prev) {
      override def apply(unit: CompilationUnit): Unit = {
        newTransformer(unit).transformUnit(unit)
      }
    }

    override val runsAfter: List[String] = "erasure" :: Nil
    override val phaseName: String = "jacoco-generated-middle"

    def newTransformer(unit: CompilationUnit) = new TypingTransformer(unit) {

      override def transform(tree: Tree): Tree = tree match {
        case dd: DefDef =>
          if (tree.symbol.isSynthetic) {
            markAsGenerated(tree)
          }
          if (dd.symbol.name.endsWith(nme.LAZY_SLOW_SUFFIX) && dd.name.stripSuffix(nme.LAZY_SLOW_SUFFIX) == tree.symbol.owner.name) {
            markAsGenerated(tree)
          }

          if (tree.symbol.isConstructor && tree.symbol.owner.isModuleClass && tree.symbol.owner.isSynthetic) {
            markAsGenerated(tree)
          }
          if (tree.symbol.isAccessor) {
            markAsGenerated(tree)
          }
          super.transform(tree);
        case _ =>
          super.transform(tree)
      }
    }
  }

  private object late extends PluginComponent with TypingTransformers {
    val global: JacocoGeneratedPlugin.this.global.type = JacocoGeneratedPlugin.this.global

    override def newPhase(prev: Phase): Phase = new StdPhase(prev) {
      override def apply(unit: CompilationUnit): Unit = {
        newTransformer(unit).transformUnit(unit)
      }
    }

    override val runsAfter: List[String] = "delambdafy" :: Nil
    override val phaseName: String = "jacoco-generated-late"

    def newTransformer(unit: CompilationUnit) = new TypingTransformer(unit) {

      override def transform(tree: Tree): Tree = tree match {
        case dd: DefDef =>
          if (tree.symbol.hasFlag(Flags.MIXEDIN)) {
            markAsGenerated(tree)
          }

          super.transform(tree);
        case _ =>
          super.transform(tree)
      }
    }
  }
}
