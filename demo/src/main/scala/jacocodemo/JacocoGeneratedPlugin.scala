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

    lazy val Objects_requireNonNull = rootMirror.getModuleIfDefined("java.util.Objects").info.member(TermName("requireNonNull")).filter(_.paramss.head.size == 1)

    def newTransformer(unit: CompilationUnit) = new TypingTransformer(unit) {

      override def transform(tree: Tree): Tree = tree match {
        // Replace synthetic:  `this.outer = if (outer == null) throw null` with this.outer = Objects.requireNonNull(outer)
        case If(Apply(Select(Ident(nme.OUTER), nme.eq), List(Literal(Constant(null)))), Throw(Literal(Constant(null))), a @ Assign(lhs, rhs)) if currentOwner.isConstructor =>
          val replaced = treeCopy.Assign(a, transform(lhs), localTyper.typed(
            gen.mkCast(gen.mkMethodCall(Objects_requireNonNull, List(transform(rhs))), rhs.tpe)
          ))
          replaced

        case dd: DefDef =>
          if (dd.symbol.isConstructor)
            getClass
          if (tree.symbol.hasFlag(Flags.MIXEDIN)) {
            markAsGenerated(tree)
          }
          super.transform(tree);
        case dd: DefDef if (dd.symbol.isConstructor) =>
          super.transform(tree)

        case _ =>
          val tree1 = super.transform(tree)
          tree1
      }
    }
  }
}
