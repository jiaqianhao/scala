/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2011, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */



package scala



import scala.util.{ Timeout, Duration }



/** This package object contains primitives for concurrent and parallel programming.
 */
package object concurrent {
  
  type ExecutionException =    java.util.concurrent.ExecutionException
  type CancellationException = java.util.concurrent.CancellationException
  type TimeoutException =      java.util.concurrent.TimeoutException
  
  /** A global execution environment for executing lightweight tasks.
   */
  lazy val executionContext =
    new default.ExecutionContextImpl
  
  /** A global service for scheduling tasks for execution.
   */
  lazy val scheduler =
    new default.SchedulerImpl
  
  private[concurrent] def currentExecutionContext: ThreadLocal[ExecutionContext] = new ThreadLocal[ExecutionContext] {
    override protected def initialValue = null
  }
  
  val handledFutureException: PartialFunction[Throwable, Throwable] = {
    case t: Throwable if isFutureThrowable(t) => t
  }
  
  // TODO rename appropriately and make public
  private[concurrent] def isFutureThrowable(t: Throwable) = t match {
    case e: Error => false
    case t: scala.util.control.ControlThrowable => false
    case i: InterruptedException => false
    case _ => true
  }
  
  private[concurrent] def resolve[T](source: Either[Throwable, T]): Either[Throwable, T] = source match {
    case Left(t: scala.runtime.NonLocalReturnControl[_]) => Right(t.value.asInstanceOf[T])
    case Left(t: scala.util.control.ControlThrowable) => Left(new ExecutionException("Boxed ControlThrowable", t))
    case Left(t: InterruptedException) => Left(new ExecutionException("Boxed InterruptedException", t))
    case Left(e: Error) => Left(new ExecutionException("Boxed Error", e))
    case _ => source
  }
  
  private val resolverFunction: PartialFunction[Throwable, Either[Throwable, _]] = {
    case t: scala.runtime.NonLocalReturnControl[_] => Right(t.value)
    case t: scala.util.control.ControlThrowable => Left(new ExecutionException("Boxed ControlThrowable", t))
    case t: InterruptedException => Left(new ExecutionException("Boxed InterruptedException", t))
    case e: Error => Left(new ExecutionException("Boxed Error", e))
    case t => Left(t)
  }
  
  private[concurrent] def resolver[T] = resolverFunction.asInstanceOf[PartialFunction[Throwable, Either[Throwable, T]]]
  
  /* concurrency constructs */
  
  def future[T](body: =>T)(implicit execCtx: ExecutionContext = executionContext): Future[T] =
    execCtx future body
  
  def promise[T]()(implicit execCtx: ExecutionContext = executionContext): Promise[T] =
    execCtx promise
  
  /** Wraps a block of code into an awaitable object. */
  def body2awaitable[T](body: =>T) = new Awaitable[T] {
    def await(atMost: Duration)(implicit cb: CanAwait) = body
  }
  
  /** Used to block on a piece of code which potentially blocks.
   *  
   *  @param body         A piece of code which contains potentially blocking or long running calls.
   *  
   *  Calling this method may throw the following exceptions:
   *  - CancellationException - if the computation was cancelled
   *  - InterruptedException - in the case that a wait within the blockable object was interrupted
   *  - TimeoutException - in the case that the blockable object timed out
   */
  def blocking[T](atMost: Duration)(body: =>T)(implicit execCtx: ExecutionContext = executionContext): T =
    executionContext.blocking(atMost)(body)
  
  /** Blocks on an awaitable object.
   *  
   *  @param awaitable    An object with a `block` method which runs potentially blocking or long running calls.
   *  
   *  Calling this method may throw the following exceptions:
   *  - CancellationException - if the computation was cancelled
   *  - InterruptedException - in the case that a wait within the blockable object was interrupted
   *  - TimeoutException - in the case that the blockable object timed out
   */
  def blocking[T](atMost: Duration)(awaitable: Awaitable[T])(implicit execCtx: ExecutionContext = executionContext): T =
    executionContext.blocking(atMost)(awaitable)
  
  /*
  def blocking[T](atMost: Duration)(body: =>T): T = blocking(body2awaitable(body), atMost)
  
  def blocking[T](awaitable: Awaitable[T], atMost: Duration): T = {
    currentExecutionContext.get match {
      case null => awaitable.await(atMost)(null) // outside - TODO - fix timeout case
      case x => x.blockingCall(awaitable) // inside an execution context thread
    }
  }
  */
  
  object await {
    def ready[T](atMost: Duration)(awaitable: Awaitable[T])(implicit execCtx: ExecutionContext = executionContext): Awaitable[T] = {
      try blocking(awaitable, atMost)
      catch { case _ => }
      awaitable
    }
    
    def result[T](atMost: Duration)(awaitable: Awaitable[T])(implicit execCtx: ExecutionContext = executionContext): T = {
      blocking(awaitable, atMost)
    }
  }
  
}



package concurrent {
  
  /** A timeout exception.
   *  
   *  Futures are failed with a timeout exception when their timeout expires.
   *  
   *  Each timeout exception contains an origin future which originally timed out.
   */
  class FutureTimeoutException(origin: Future[_], message: String) extends TimeoutException(message) {
    def this(origin: Future[_]) = this(origin, "Future timed out.")
  }
  
}


