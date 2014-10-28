package akka.event

import language.existentials
import akka.actor._
import scala.annotation.implicitNotFound

/**
 * This trait defines the interface to be provided by a “log source formatting
 * rule” as used by [[akka.event.Logging]]’s `apply`/`create` method.
 *
 * See the companion object for default implementations.
 *
 * Example:
 * {{{
 * trait MyType { // as an example
 *   def name: String
 * }
 *
 * implicit val myLogSourceType: LogSource[MyType] = new LogSource[MyType] {
 *   def genString(a: MyType) = a.name
 * }
 *
 * class MyClass extends MyType {
 *   val log = Logging(eventStream, this) // will use "hallo" as logSource
 *   def name = "hallo"
 * }
 * }}}
 *
 * The second variant is used for including the actor system’s address:
 * {{{
 * trait MyType { // as an example
 *   def name: String
 * }
 *
 * implicit val myLogSourceType: LogSource[MyType] = new LogSource[MyType] {
 *   def genString(a: MyType) = a.name
 *   def genString(a: MyType, s: ActorSystem) = a.name + "," + s
 * }
 *
 * class MyClass extends MyType {
 *   val sys = ActorSyste("sys")
 *   val log = Logging(sys, this) // will use "hallo,akka://sys" as logSource
 *   def name = "hallo"
 * }
 * }}}
 *
 * The default implementation of the second variant will just call the first.
 */
@implicitNotFound("Cannot find LogSource for ${T} please see ScalaDoc for LogSource for how to obtain or construct one.") trait LogSource[-T] {
  def genString(t: T): String
  def genString(t: T, system: ActorSystem): String = genString(t)
  def getClazz(t: T): Class[_] = t.getClass
}

/**
 * This is a “marker” class which is inserted as originator class into
 * [[akka.event.Logging.LogEvent]] when the string representation was supplied
 * directly.
 */
class DummyClassForStringSources

/**
 * Main entry point for Akka logging: log levels and message types (aka
 * channels) defined for the main transport medium, the main event bus. The
 * recommended use is to obtain an implementation of the Logging trait with
 * suitable and efficient methods for generating log events:
 *
 * <pre><code>
 * val log = Logging(&lt;bus&gt;, &lt;source object&gt;)
 * ...
 * log.info("hello world!")
 * </code></pre>
 *
 * The source object is used in two fashions: its `Class[_]` will be part of
 * all log events produced by this logger, plus a string representation is
 * generated which may contain per-instance information, see `apply` or `create`
 * below.
 *
 * Loggers are attached to the level-specific channels <code>Error</code>,
 * <code>Warning</code>, <code>Info</code> and <code>Debug</code> as
 * appropriate for the configured (or set) log level. If you want to implement
 * your own, make sure to handle these four event types plus the <code>InitializeLogger</code>
 * message which is sent before actually attaching it to the logging bus.
 *
 * Logging is configured by setting (some of) the following:
 *
 * <pre><code>
 * akka {
 *   loggers = ["akka.slf4j.Slf4jLogger"] # for example
 *   loglevel = "INFO"        # used when normal logging ("loggers") has been started
 *   stdout-loglevel = "WARN" # used during application start-up until normal logging is available
 * }
 * </code></pre>
 */
object Logging {

  /**
   * Returns a 'safe' getSimpleName for the provided object's Class
   * @param obj
   * @return the simple name of the given object's Class
   */
  def simpleName(obj: AnyRef): String = simpleName(obj.getClass)

  /**
   * Returns a 'safe' getSimpleName for the provided Class
   * @param obj
   * @return the simple name of the given Class
   */
  def simpleName(clazz: Class[_]): String = {
    val n = clazz.getName
    val i = n.lastIndexOf('.')
    n.substring(i + 1)
  }

  /**
   * Marker trait for annotating LogLevel, which must be Int after erasure.
   */
  case class LogLevel(asInt: Int) extends AnyVal {
    @inline final def >=(other: LogLevel): Boolean = asInt >= other.asInt
    @inline final def <=(other: LogLevel): Boolean = asInt <= other.asInt
    @inline final def >(other: LogLevel): Boolean = asInt > other.asInt
    @inline final def <(other: LogLevel): Boolean = asInt < other.asInt
  }

  /**
   * Log level in numeric form, used when deciding whether a certain log
   * statement should generate a log event. Predefined levels are ErrorLevel (1)
   * to DebugLevel (4). In case you want to add more levels, loggers need to
   * be subscribed to their event bus channels manually.
   */
  final val ErrorLevel = LogLevel(1)
  final val WarningLevel = LogLevel(2)
  final val InfoLevel = LogLevel(3)
  final val DebugLevel = LogLevel(4)

  /**
   * Base type of LogEvents
   */
  sealed trait LogEvent {
    /**
     * When this LogEvent was created according to System.currentTimeMillis
     */
    val timestamp: Long = System.currentTimeMillis

    /**
     * The LogLevel of this LogEvent
     */
    def level: LogLevel

    /**
     * The source of this event
     */
    def logSource: String

    /**
     * The class of the source of this event
     */
    def logClass: Class[_]

    /**
     * The message, may be any object or null.
     */
    def message: Any
  }

  /**
   * For ERROR Logging
   */
  case class Error(cause: Throwable, logSource: String, logClass: Class[_], message: Any = "") extends LogEvent {
    def this(logSource: String, logClass: Class[_], message: Any) = this(Error.NoCause, logSource, logClass, message)

    override def level = ErrorLevel
  }

  object Error {
    def apply(logSource: String, logClass: Class[_], message: Any) =
      new Error(NoCause, logSource, logClass, message)

    /** Null Object used for errors without cause Throwable */
    object NoCause extends Throwable
  }
  def noCause = Error.NoCause

  /**
   * For WARNING Logging
   */
  case class Warning(logSource: String, logClass: Class[_], message: Any = "") extends LogEvent {
    override def level = WarningLevel
  }

  /**
   * For INFO Logging
   */
  case class Info(logSource: String, logClass: Class[_], message: Any = "") extends LogEvent {
    override def level = InfoLevel
  }

  /**
   * For DEBUG Logging
   */
  case class Debug(logSource: String, logClass: Class[_], message: Any = "") extends LogEvent {
    override def level = DebugLevel
  }

  type MDC = Map[String, Any]

  val emptyMDC: MDC = Map()

}

/**
 * Logging wrapper to make nicer and optimize: provide template versions which
 * evaluate .toString only if the log level is actually enabled. Typically used
 * by obtaining an implementation from the Logging object:
 *
 * <code><pre>
 * val log = Logging(&lt;bus&gt;, &lt;source object&gt;)
 * ...
 * log.info("hello world!")
 * </pre></code>
 *
 * All log-level methods support simple interpolation templates with up to four
 * arguments placed by using <code>{}</code> within the template (first string
 * argument):
 *
 * <code><pre>
 * log.error(exception, "Exception while processing {} in state {}", msg, state)
 * </pre></code>
 */
trait LoggingAdapter {

  type MDC = Logging.MDC
  def mdc = Logging.emptyMDC

  /*
   * implement these as precisely as needed/possible: always returning true
   * just makes the notify... methods be called every time.
   */
  def isErrorEnabled: Boolean
  def isWarningEnabled: Boolean
  def isInfoEnabled: Boolean
  def isDebugEnabled: Boolean

  /*
   * These actually implement the passing on of the messages to be logged.
   * Will not be called if is...Enabled returned false.
   */
  protected def notifyError(message: String): Unit
  protected def notifyError(cause: Throwable, message: String): Unit
  protected def notifyWarning(message: String): Unit
  protected def notifyInfo(message: String): Unit
  protected def notifyDebug(message: String): Unit

  /*
   * The rest is just the widening of the API for the user's convenience.
   */

  /**
   * Log message at error level, including the exception that caused the error.
   * @see [[LoggingAdapter]]
   */
  def error(cause: Throwable, message: String): Unit = { if (isErrorEnabled) notifyError(cause, message) }
  /**
   * Message template with 1 replacement argument.
   * @see [[LoggingAdapter]]
   */
  def error(cause: Throwable, template: String, arg1: Any): Unit = { if (isErrorEnabled) notifyError(cause, format1(template, arg1)) }
  /**
   * Message template with 2 replacement arguments.
   * @see [[LoggingAdapter]]
   */
  def error(cause: Throwable, template: String, arg1: Any, arg2: Any): Unit = { if (isErrorEnabled) notifyError(cause, format(template, arg1, arg2)) }
  /**
   * Message template with 3 replacement arguments.
   * @see [[LoggingAdapter]]
   */
  def error(cause: Throwable, template: String, arg1: Any, arg2: Any, arg3: Any): Unit = { if (isErrorEnabled) notifyError(cause, format(template, arg1, arg2, arg3)) }
  /**
   * Message template with 4 replacement arguments.
   * @see [[LoggingAdapter]]
   */
  def error(cause: Throwable, template: String, arg1: Any, arg2: Any, arg3: Any, arg4: Any): Unit = { if (isErrorEnabled) notifyError(cause, format(template, arg1, arg2, arg3, arg4)) }

  /**
   * Log message at error level, without providing the exception that caused the error.
   * @see [[LoggingAdapter]]
   */
  def error(message: String): Unit = { if (isErrorEnabled) notifyError(message) }
  /**
   * Message template with 1 replacement argument.
   * @see [[LoggingAdapter]]
   */
  def error(template: String, arg1: Any): Unit = { if (isErrorEnabled) notifyError(format1(template, arg1)) }
  /**
   * Message template with 2 replacement arguments.
   * @see [[LoggingAdapter]]
   */
  def error(template: String, arg1: Any, arg2: Any): Unit = { if (isErrorEnabled) notifyError(format(template, arg1, arg2)) }
  /**
   * Message template with 3 replacement arguments.
   * @see [[LoggingAdapter]]
   */
  def error(template: String, arg1: Any, arg2: Any, arg3: Any): Unit = { if (isErrorEnabled) notifyError(format(template, arg1, arg2, arg3)) }
  /**
   * Message template with 4 replacement arguments.
   * @see [[LoggingAdapter]]
   */
  def error(template: String, arg1: Any, arg2: Any, arg3: Any, arg4: Any): Unit = { if (isErrorEnabled) notifyError(format(template, arg1, arg2, arg3, arg4)) }

  /**
   * Log message at warning level.
   * @see [[LoggingAdapter]]
   */
  def warning(message: String): Unit = { if (isWarningEnabled) notifyWarning(message) }
  /**
   * Message template with 1 replacement argument.
   * @see [[LoggingAdapter]]
   */
  def warning(template: String, arg1: Any): Unit = { if (isWarningEnabled) notifyWarning(format1(template, arg1)) }
  /**
   * Message template with 2 replacement arguments.
   * @see [[LoggingAdapter]]
   */
  def warning(template: String, arg1: Any, arg2: Any): Unit = { if (isWarningEnabled) notifyWarning(format(template, arg1, arg2)) }
  /**
   * Message template with 3 replacement arguments.
   * @see [[LoggingAdapter]]
   */
  def warning(template: String, arg1: Any, arg2: Any, arg3: Any): Unit = { if (isWarningEnabled) notifyWarning(format(template, arg1, arg2, arg3)) }
  /**
   * Message template with 4 replacement arguments.
   * @see [[LoggingAdapter]]
   */
  def warning(template: String, arg1: Any, arg2: Any, arg3: Any, arg4: Any): Unit = { if (isWarningEnabled) notifyWarning(format(template, arg1, arg2, arg3, arg4)) }

  /**
   * Log message at info level.
   * @see [[LoggingAdapter]]
   */
  def info(message: String) { if (isInfoEnabled) notifyInfo(message) }
  /**
   * Message template with 1 replacement argument.
   * @see [[LoggingAdapter]]
   */
  def info(template: String, arg1: Any): Unit = { if (isInfoEnabled) notifyInfo(format1(template, arg1)) }
  /**
   * Message template with 2 replacement arguments.
   * @see [[LoggingAdapter]]
   */
  def info(template: String, arg1: Any, arg2: Any): Unit = { if (isInfoEnabled) notifyInfo(format(template, arg1, arg2)) }
  /**
   * Message template with 3 replacement arguments.
   * @see [[LoggingAdapter]]
   */
  def info(template: String, arg1: Any, arg2: Any, arg3: Any): Unit = { if (isInfoEnabled) notifyInfo(format(template, arg1, arg2, arg3)) }
  /**
   * Message template with 4 replacement arguments.
   * @see [[LoggingAdapter]]
   */
  def info(template: String, arg1: Any, arg2: Any, arg3: Any, arg4: Any): Unit = { if (isInfoEnabled) notifyInfo(format(template, arg1, arg2, arg3, arg4)) }

  /**
   * Log message at debug level.
   * @see [[LoggingAdapter]]
   */
  def debug(message: String) { if (isDebugEnabled) notifyDebug(message) }
  /**
   * Message template with 1 replacement argument.
   * @see [[LoggingAdapter]]
   */
  def debug(template: String, arg1: Any): Unit = { if (isDebugEnabled) notifyDebug(format1(template, arg1)) }
  /**
   * Message template with 2 replacement arguments.
   * @see [[LoggingAdapter]]
   */
  def debug(template: String, arg1: Any, arg2: Any): Unit = { if (isDebugEnabled) notifyDebug(format(template, arg1, arg2)) }
  /**
   * Message template with 3 replacement arguments.
   * @see [[LoggingAdapter]]
   */
  def debug(template: String, arg1: Any, arg2: Any, arg3: Any): Unit = { if (isDebugEnabled) notifyDebug(format(template, arg1, arg2, arg3)) }
  /**
   * Message template with 4 replacement arguments.
   * @see [[LoggingAdapter]]
   */
  def debug(template: String, arg1: Any, arg2: Any, arg3: Any, arg4: Any): Unit = { if (isDebugEnabled) notifyDebug(format(template, arg1, arg2, arg3, arg4)) }

  /**
   * Log message at the specified log level.
   */
  def log(level: Logging.LogLevel, message: String) { if (isEnabled(level)) notifyLog(level, message) }
  /**
   * Message template with 1 replacement argument.
   */
  def log(level: Logging.LogLevel, template: String, arg1: Any): Unit = { if (isEnabled(level)) notifyLog(level, format1(template, arg1)) }
  /**
   * Message template with 2 replacement arguments.
   */
  def log(level: Logging.LogLevel, template: String, arg1: Any, arg2: Any): Unit = { if (isEnabled(level)) notifyLog(level, format(template, arg1, arg2)) }
  /**
   * Message template with 3 replacement arguments.
   */
  def log(level: Logging.LogLevel, template: String, arg1: Any, arg2: Any, arg3: Any): Unit = { if (isEnabled(level)) notifyLog(level, format(template, arg1, arg2, arg3)) }
  /**
   * Message template with 4 replacement arguments.
   */
  def log(level: Logging.LogLevel, template: String, arg1: Any, arg2: Any, arg3: Any, arg4: Any): Unit = { if (isEnabled(level)) notifyLog(level, format(template, arg1, arg2, arg3, arg4)) }

  /**
   * @return true if the specified log level is enabled
   */
  final def isEnabled(level: Logging.LogLevel): Boolean = level match {
    case Logging.ErrorLevel   ⇒ isErrorEnabled
    case Logging.WarningLevel ⇒ isWarningEnabled
    case Logging.InfoLevel    ⇒ isInfoEnabled
    case Logging.DebugLevel   ⇒ isDebugEnabled
  }

  final def notifyLog(level: Logging.LogLevel, message: String): Unit = level match {
    case Logging.ErrorLevel   ⇒ if (isErrorEnabled) notifyError(message)
    case Logging.WarningLevel ⇒ if (isWarningEnabled) notifyWarning(message)
    case Logging.InfoLevel    ⇒ if (isInfoEnabled) notifyInfo(message)
    case Logging.DebugLevel   ⇒ if (isDebugEnabled) notifyDebug(message)
  }

  private def format1(t: String, arg: Any): String = arg match {
    case a: Array[_] if !a.getClass.getComponentType.isPrimitive ⇒ format(t, a: _*)
    case a: Array[_] ⇒ format(t, (a map (_.asInstanceOf[AnyRef]): _*))
    case x ⇒ format(t, x)
  }

  def format(t: String, arg: Any*): String = {
    val sb = new java.lang.StringBuilder(64)
    var p = 0
    var rest = t
    while (p < arg.length) {
      val index = rest.indexOf("{}")
      if (index == -1) {
        sb.append(rest).append(" WARNING arguments left: ").append(arg.length - p)
        rest = ""
        p = arg.length
      } else {
        sb.append(rest.substring(0, index)).append(arg(p))
        rest = rest.substring(index + 2)
        p += 1
      }
    }
    sb.append(rest).toString
  }
}
