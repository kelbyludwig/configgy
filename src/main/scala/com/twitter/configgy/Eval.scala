package com.twitter.configgy

import java.io.{File, FileWriter}
import java.math.BigInteger
import java.net.{URL, URLClassLoader}
import java.security.MessageDigest
import java.util.jar._
import java.util.Random
import scala.io.Source
import scala.runtime._
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.{Global, Settings}

import scala.collection.mutable
import scala.tools.nsc.interpreter.AbstractFileClassLoader
import scala.tools.nsc.io.VirtualDirectory
import scala.tools.nsc.reporters.AbstractReporter
import scala.tools.nsc.util.{BatchSourceFile, Position}

/**
 * Evaluate a file or string and return the result.
 */
object Eval {
  // do not look at the man behind the curtain!
  private val compilerPath = jarPathOfClass("scala.tools.nsc.Interpreter")
  private val libPath = jarPathOfClass("scala.ScalaObject")

  private val jvmId = java.lang.Math.abs(new Random().nextInt())

  val compiler = new StringCompiler(2)

  private def uniqueId(code: String): String = {
    val digest = MessageDigest.getInstance("SHA-1").digest(code.getBytes())
    val sha = new BigInteger(1, digest).toString(16)
    sha + "_" + jvmId
  }

  /*
   * Wrap source code in a new class with an apply method.
   */
  private def wrapCodeInClass(className: String, code: String) = {
    "class " + className + " extends (() => Any) {\n" +
    "  def apply() = {\n" +
    code + "\n" +
    "  }\n" +
    "}\n"
  }

  /*
   * For a given FQ classname, trick the resource finder into telling us the containing jar.
   */
  private def jarPathOfClass(className: String) = {
    val resource = className.split('.').mkString("/", "/", ".class")
    val path = getClass.getResource(resource).getPath
    val indexOfFile = path.indexOf("file:") + 5
    val indexOfSeparator = path.lastIndexOf('!')
    path.substring(indexOfFile, indexOfSeparator)
  }

  def apply[T](code: String): T = {
    val id = uniqueId(code)
    val className = "Evaluator__" + id
    val cls = compiler(wrapCodeInClass(className, code), className)
    cls.getConstructor().newInstance().asInstanceOf[() => Any].apply().asInstanceOf[T]
  }

  /**
   * Dynamic scala compiler. Lots of (slow) state is created, so it may be advantageous to keep
   * around one of these and reuse it.
   */
  class StringCompiler(lineOffset: Int) {
    val virtualDirectory = new VirtualDirectory("(memory)", None)

    val settings = new Settings
    settings.deprecation.value = true // enable detailed deprecation warnings
    settings.unchecked.value = true // enable detailed unchecked warnings
    settings.outputDirs.setSingleOutput(virtualDirectory)

    // FIXME: add our own jar & deps to the classpath.
    val pathList = List(compilerPath, libPath)
    val pathString = pathList.mkString(File.pathSeparator)
    settings.bootclasspath.value = pathString
    settings.classpath.value = pathString

    val reporter = new AbstractReporter {
      val settings = StringCompiler.this.settings
      val messages = new mutable.ListBuffer[List[String]]

      def display(pos: Position, message: String, severity: Severity) {
        severity.count += 1
        val severityName = severity match {
          case ERROR   => "error: "
          case WARNING => "warning: "
          case _ => ""
        }
        messages += (severityName + "line " + (pos.line - lineOffset) + ": " + message) ::
          (if (pos.isDefined) {
            pos.inUltimateSource(pos.source).lineContent.stripLineEnd ::
              (" " * (pos.column - 1) + "^") ::
              Nil
          } else {
            Nil
          })
      }

      def displayPrompt {
        // no.
      }

      override def reset {
        messages.clear()
      }
    }

    val global = new Global(settings, reporter)

    /*
     * Class loader for finding classes compiled by this StringCompiler.
     * After each reset, this class loader will not be able to find old compiled classes.
     */
    val classLoader = new AbstractFileClassLoader(virtualDirectory, this.getClass.getClassLoader)

    def reset() {
      // grumpy comment about these side-effect methods not taking parens.
      virtualDirectory.clear
      reporter.reset
    }

    /**
     * Compile scala code. It can be found using the above class loader.
     */
    def apply(code: String) {
      val compiler = new global.Run
      val sourceFiles = List(new BatchSourceFile("(inline)", code))
      val s1 = System.currentTimeMillis
      try {
        compiler.compileSources(sourceFiles)
      } catch {
        case e: Error =>
          println("boo.")
      }
      val s2 = System.currentTimeMillis
      println("time to compile --> " + (s2 - s1) + " msec")

      if (reporter.hasErrors || reporter.WARNING.count > 0) {
        throw new CompilerException(reporter.messages.toList)
      }
    }

    /**
     * Reset the compiler, compile a new class, load it, and return it. Thread-safe.
     */
    def apply(code: String, className: String): Class[_] = synchronized {
      reset()
      apply(code)
      classLoader.loadClass(className)
    }
  }

  class CompilerException(val messages: List[List[String]]) extends Exception("Compiler exception")
}
