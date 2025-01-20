package jacocodemo

import org.jacoco.report.FileMultiReportOutput
import org.jacoco.report.html.HTMLFormatter
import org.jacoco.report.xml.XMLFormatter

import java.io.{BufferedOutputStream, ByteArrayOutputStream, PrintStream}
import java.nio.file.Files
import scala.annotation.nowarn
import scala.reflect.internal.Flags
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
    val result = new CoreTutorial().execute(temp.toFile, "demo.Coverage")
    import scala.jdk.CollectionConverters._

    val coverage = result.coverage()
    // for loop
    val missedCount = coverage.getMethodCounter.getMissedCount()
    if (missedCount != 0) {
      println(coverage.getMethodCounter.toString + "\n" + result.toString)
      val xmlFormatter = new XMLFormatter
      val out = new ByteArrayOutputStream()
      val visitor = xmlFormatter.createVisitor(out)

      // Initialize the report with all of the execution and session
      // information. At this point the report doesn't know about the
      // structure of the report being created
      visitor.visitInfo(result.sessionInfos().getInfos, result.executionData().getContents)

      // Populate the report structure with the bundle coverage information.
      // Call visitGroup if you need groups in your report.
      visitor.visitBundle(coverage, null)

      // Signal end of structure information to allow report to write all
      // information out
      visitor.visitEnd()
      val csv = out.toString(java.nio.charset.StandardCharsets.UTF_8)
      println(csv)
    }
  }
}

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

