package jacocodemo

import org.jacoco.report.xml.XMLFormatter
import org.junit.Test

import java.io.ByteArrayOutputStream
import java.nio.file.Files
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
  }

  @Test
  def caseClass: Unit = test(code.caseClass)

  @Test
  def pattern1: Unit = {
    // TODO
    //  public foo(Lscala/Option;)V
    //    ALOAD 1
    //    ASTORE 3
    //    ALOAD 3
    //    INSTANCEOF scala/Some
    //    IFEQ L0
    //    GETSTATIC scala/runtime/BoxedUnit.UNIT : Lscala/runtime/BoxedUnit;
    //    POP
    //    RETURN
    //   L0
    //    GOTO L1
    //   L1
    //    GETSTATIC scala/None$.MODULE$ : Lscala/None$;
    //    ALOAD 3
    //    INVOKEVIRTUAL java/lang/Object.equals (Ljava/lang/Object;)Z
    //    IFEQ L2
    //    GETSTATIC scala/runtime/BoxedUnit.UNIT : Lscala/runtime/BoxedUnit;
    //    POP
    //    RETURN

    // TODO: Exclude these with a filter?

    //   L2
    //    GOTO L3
    //   L3
    //    NEW scala/MatchError
    //    DUP
    //    ALOAD 3
    //    INVOKESPECIAL scala/MatchError.<init> (Ljava/lang/Object;)V
    //    ATHROW
    //    MAXSTACK = 3
    //    MAXLOCALS = 4

    test(code.pattern1, instruction = true)
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
        println(AsmUtils.textify(AsmUtils.classFromBytes(Files.readAllBytes(temp.resolve("demo/Coverage.class")))))
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


