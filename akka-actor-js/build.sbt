import akkajs.AkkaJSBuild

//import akkajs.Exclude

import scala.scalajs.sbtplugin.ScalaJSPlugin._ // import `%%%` extension method

scalaJSSettings

utest.jsrunner.Plugin.utestJsSettings

AkkaJSBuild.defaultSettings

unmanagedSourceDirectories in Compile += baseDirectory.value / ".." / "akka-actor" / "src" / "main" / "scala"

excludeFilter in unmanagedSources := HiddenFileFilter //|| Exclude.excludeFromModuleOne

ScalaJSKeys.persistLauncher := true

ScalaJSKeys.persistLauncher in Test := false

// only for testing:

libraryDependencies += "org.scala-lang.modules.scalajs" %%% "scalajs-dom" % "0.6" // MIT

libraryDependencies += "org.scalajs" %%% "scalajs-pickling" % "0.3.1"

libraryDependencies += "com.lihaoyi" %% "utest" % "0.2.0"

testFrameworks += new TestFramework("utest.runner.JvmFramework")

