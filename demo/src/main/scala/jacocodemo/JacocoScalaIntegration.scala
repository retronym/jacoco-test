package jacocodemo

import org.jacoco.report.xml.XMLFormatter
import org.junit.Test

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.Objects
import scala.annotation.nowarn
import scala.tools.nsc.backend.jvm.AsmUtils
import scala.tools.nsc.{Global, Settings}
import scala.tools.nsc.plugins.Plugin
import scala.tools.nsc.reporters.StoreReporter

class JacocoScalaIntegration {

  object code {
    def wrap(code: String): String =
      """
        |package demo
        |
        |class Coverage extends Runnable {
        |    %s
        |}
        | """.stripMargin.format(code)

    val caseClass = wrap(
      """
        |case class C(i: Int)
        |def run: Unit = {
        |  new C(1)
        |}
        |""".stripMargin)
    val pattern1 = wrap(
      """
        |def foo(a: Option[String]): Unit = {
        |  a match {
        |   case Some(c) =>
        |   case None =>
        |  }
        |}
        |def run: Unit = {
        |  foo(Some("a"))
        |  foo(None)
        |}
        |""".stripMargin)

    val pattern2 = wrap(
      """
        |
        |sealed trait Base
        |final case class Base_1(sameName: Some[Any]) extends Base
        |final case class Base_2(sameName: Nested) extends Base
        |
        |sealed trait Nested
        |final case class Nested_1(x: Any) extends Nested
        |final case class Nested_2(y: Any) extends Nested
        |
        |def foo(b: Base): Unit = b match {
        |  case Base_1(Some(_)) =>
        |  case Base_2(Nested_1(_)) =>
        |  case Base_2(Nested_2(_)) =>
        |}
        |def run: Unit = {
        |  foo(new Base_1(Some("a")))
        |  foo(new Base_2(Nested_1("a")))
        |  foo(new Base_2(Nested_2("a")))
        |}
        |""".stripMargin)
  }

  @Test
  def caseClass: Unit = test(code.caseClass)

  @Test
  def syntheticThrowNewMatchErrorIgnored: Unit = {
    test(code.pattern1, instruction = true)
  }

  @Test
  def syntheticPattern2: Unit = {
    test(code.pattern2, instruction = true)
  }

  private def test(code: String, method: Boolean = true, line: Boolean = true, instruction: Boolean = false): Unit = {
    val result: JacocoFacade.Result = compileRunAndAnalyze(code)
    val coverage = result.coverage()
    val methodOk = if (method) coverage.getMethodCounter.getMissedCount == 0 else true
    val lineOk = if (line) coverage.getLineCounter.getMissedCount == 0 else true
    val instructionOk = if (instruction) coverage.getInstructionCounter.getMissedCount == 0 else true

    val ok = methodOk && lineOk && instructionOk
    if (!ok) {
      throw new RuntimeException("Incomplete coverage: " + xmlReport(result))
    }
  }

  private def xmlReport(result: JacocoFacade.Result) = {
    val xmlFormatter = new XMLFormatter
    val out = new ByteArrayOutputStream()
    val visitor = xmlFormatter.createVisitor(out)
    visitor.visitInfo(result.sessionInfos().getInfos, result.executionData().getContents)
    visitor.visitBundle(result.coverage(), null)
    visitor.visitEnd()
    val xml = out.toString(java.nio.charset.StandardCharsets.UTF_8)
    xml
  }

  private def compileRunAndAnalyze(code: String): JacocoFacade.Result = {
    val settings = new Settings()
    settings.embeddedDefaults(getClass.getClassLoader)
    val isInSBT = !settings.classpath.isSetByUser
    if (isInSBT) settings.usejavacp.value = true
    settings.release.value = "23"
    settings.target.value = settings.release.value
    settings.Xprint.value = List("patmat")
    val storeReporter = new StoreReporter(settings)
    val temp = Files.createTempDirectory("jacoco")
    try {
      settings.outputDirs.setSingleOutput(temp.toString)
      val global = new Global(settings, storeReporter) {
        self =>
        @nowarn("cat=deprecation&msg=early initializers")
        object plugin extends {
          val global: self.type = self
        } with JacocoGeneratedPlugin

        override protected def loadPlugins(): List[Plugin] = plugin :: Nil
      }
      import global._
      val run = new Run()
      run.compileUnits(newCompilationUnit(code) :: Nil, run.parserPhase)
      if (storeReporter.hasErrors) throw new RuntimeException("Compilation failed: " + storeReporter.infos.toSeq.map(_.toString()))
      val debug = false
      if (debug) {
        val path = temp.resolve("demo/Coverage.class").toAbsolutePath
        import scala.tools.asm._
        import scala.tools.asm.tree._
        import scala.tools.nsc.backend.jvm.ClassNode1
        def classFromBytes(bytes: Array[Byte]): ClassNode = {
          val node = new ClassNode1()
          new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES)

          node
        }
        println(AsmUtils.textify(classFromBytes(Files.readAllBytes(path))))
      }

      new JacocoFacade().execute(temp.toFile, "demo.Coverage")
    } finally {
      deleteRecursive(temp.toFile)
    }
  }

  def deleteRecursive(file: java.io.File): Unit = {
    if (file.isDirectory) {
      file.listFiles().foreach(deleteRecursive)
    }
    file.delete()
  }
}


