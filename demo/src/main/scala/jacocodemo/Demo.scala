package jacocodemo

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.file.Files
import scala.annotation.nowarn
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.transform.TypingTransformers
import scala.tools.nsc.{Global, Phase}

object JacocoScalaIntegration {

  def main(args: Array[String]): Unit = {
    val settings = new scala.tools.nsc.Settings()
    settings.embeddedDefaults(getClass.getClassLoader)
    val isInSBT = !settings.classpath.isSetByUser
    if (isInSBT) settings.usejavacp.value = true

    settings.release.value = "23"
    settings.target.value = settings.release.value
    val temp = Files.createTempDirectory("jacoco")
    settings.outputDirs.setSingleOutput(temp.toString)
    val global = new Global(settings) {
      self =>
      @nowarn("cat=deprecation&msg=early initializers")
      object late extends {
        val global: self.type = self
      } with JacocoGeneratedPlugin

      override protected def loadPlugins(): List[Plugin] = late :: Nil
    }
    import global._
    val run = new Run()
    run.compileUnits(newCompilationUnit(
      """
        |package demo
        |
        |class Coverage extends Runnable {
        |  case class C(i: Int)
        |  def run: Unit = {
        |    new C(1)
        |  }
        |}
        | """.stripMargin) :: Nil, run.parserPhase)
    
    if (reporter.hasErrors) sys.exit(1)
    val loader = new java.net.URLClassLoader(Array(temp.toUri.toURL), this.getClass.getClassLoader)
    val out = new ByteArrayOutputStream()
    new CoreTutorial(new PrintStream(out)).execute(temp.toFile, "demo.Coverage")
    println(new String(out.toByteArray))
  }
}

abstract class JacocoGeneratedPlugin extends Plugin {

  import global._
  (t: ExistentialType) => t.typeArgs

  override val description: String = "jacoco-generated"
  override val name: String = "jacoco-generated"

  override val components: List[PluginComponent] = List(new PluginComponent with TypingTransformers {
    val global: JacocoGeneratedPlugin.this.global.type = JacocoGeneratedPlugin.this.global

    override def newPhase(prev: Phase): Phase = new StdPhase(prev) {
      override def apply(unit: CompilationUnit): Unit = {
        newTransformer(unit).transformUnit(unit)
      }
    }

    override val runsAfter: List[String] = "erasure" :: Nil
    override val phaseName: String = "jacoco-generated"

    def newTransformer(unit: CompilationUnit) = new TypingTransformer(unit) {

      override def transform(tree: Tree): Tree = tree match {
        case dd: DefDef =>
          if (tree.symbol.isSynthetic) {
            tree.symbol.withAnnotations(List(AnnotationInfo(typeOf[Generated], Nil, Nil)))
          }
          if (tree.symbol.name.endsWith(nme.LAZY_SLOW_SUFFIX) && tree.symbol.name.stripSuffix(nme.LAZY_SLOW_SUFFIX) == tree.symbol.owner.name) {
            tree.symbol.withAnnotations(List(AnnotationInfo(typeOf[Generated], Nil, Nil)))
          }
          if (tree.symbol.isConstructor && tree.symbol.owner.isModuleClass && tree.symbol.owner.isSynthetic) {
            tree.symbol.withAnnotations(List(AnnotationInfo(typeOf[Generated], Nil, Nil)))
          }
          if (tree.symbol.isAccessor) {
            tree.symbol.withAnnotations(List(AnnotationInfo(typeOf[Generated], Nil, Nil)))
          }

          super.transform(tree);
        case _ =>
          super.transform(tree)
      }
    }
  })
}
