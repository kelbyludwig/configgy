/*
 * Copyright 2010 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.configgy

import java.io.File
import java.util.Properties
import scala.collection.mutable
import net.lag.extensions._


/**
 * Use information in a local `build.properties` file to determine runtime
 * environment info like the package name, version, and installation path.
 * This can be used to automatically load config files from a `config/` path
 * relative to the executable jar.
 *
 * An example of how to generate a `build.properties` file is included in
 * sbt stanard-project: <http://github.com/twitter/standard-project>
 *
 * You have to pass in a class from your package in order to identify the
 * location of the `build.properties` file.
 */
class RuntimeEnvironment(cls: Class[_]) {
  // load build info, if present.
  private var buildProperties = new Properties
  try {
    buildProperties.load(cls.getResource("build.properties").openStream)
  } catch {
    case _ =>
  }

  val jarName = buildProperties.getProperty("name", "unknown")
  val jarVersion = buildProperties.getProperty("version", "0.0")
  val jarBuild = buildProperties.getProperty("build_name", "unknown")
  val jarBuildRevision = buildProperties.getProperty("build_revision", "unknown")
  val stageName = System.getProperty("stage", "production")


  /**
   * Return the path this jar was executed from. Depends on the presence of
   * a valid `build.properties` file. Will return `None` if it couldn't
   * figure out the environment.
   */
  lazy val jarPath: Option[String] = {
    val pattern = ("(.*?)" + jarName + "(?:_[\\d.]+)?-" + jarVersion + "\\.jar$").r
    (System.getProperty("java.class.path") split System.getProperty("path.separator")).map { elem =>
      elem match {
        case pattern(path) => Some(new File(path).getCanonicalPath)
        case _ => None
      }
    }.toSeq.flatten.headOption
  }

  /**
   * Config filename, as determined from this jar's runtime path, possibly
   * overridden by a command-line option.
   */
  var configFilename: String = jarPath match {
    case Some(path) => path + "/config/" + stageName + ".scala"
    case None => "/etc/" + jarName + ".scala"
  }

  /**
   * Perform baseline command-line argument parsing. Responds to `--help`,
   * `--version`, and `-f` (which overrides the config filename).
   */
  def parseArgs(args: List[String]): Unit = {
    args match {
      case "-f" :: filename :: xs =>
        configFilename = filename
        parseArgs(xs)
      case "--help" :: xs =>
        help
      case "--version" :: xs =>
        println("%s %s %s %s".format(jarName, jarVersion, jarBuild, jarBuildRevision))
      case Nil =>
      case unknown :: _ =>
        println("Unknown command-line option: " + unknown)
        help
    }
  }

  private def help = {
    println
    println("%s %s (%s)".format(jarName, jarVersion, jarBuild))
    println("options:")
    println("    -f <filename>")
    println("        load config file (default: %s)".format(configFilename))
    println("    --version")
    println("        show version information")
    println
    System.exit(0)
  }

  /**
   * Parse any command-line arguments (using `parseArgs`) and then load the
   * config file as determined by `configFilename` into the default config
   * block.
   */
  def load(args: Array[String]) = {
    parseArgs(args.toList)
//    Configgy.configure(configFilename)
  }
}
