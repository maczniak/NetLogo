// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.prim

import org.nlogo.nvm.{ CommandTask, Context, Procedure, Reporter }

class _commandtask(val argCount: Int, var proc: Procedure) extends Reporter {

  override def toString =
    super.toString +
      // proc is null after ExpressionParser but before LambdaLifter
      Option(proc)
        .map(p => ":" + p.displayName)
        .getOrElse("")

  override def report(c: Context): AnyRef =
    CommandTask(
      procedure = proc,
      formals = proc.taskFormals.reverse.dropWhile(_ == null).reverse.toArray,
      lets = c.allLets,
      locals = c.activation.args)

}
