// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.prim.plot

import org.nlogo.api.{ CommandRunnable}
import org.nlogo.core.Syntax
import org.nlogo.core.{ I18N, LogoList }
import org.nlogo.nvm.{ Command, Context, EngineException, Instruction, Reporter }
import org.nlogo.plot.PlotManager

//
// base classes
//

trait Helpers extends Instruction {
  def plotManager =
    workspace.plotManager.asInstanceOf[PlotManager]
  def currentPlot(context: Context) =
    plotManager.currentPlot.getOrElse(
      throw new EngineException(
        context, this,
        I18N.errors.get("org.nlogo.plot.noPlotSelected")))
  def currentPen(context: Context) = {
    val plot = currentPlot(context)
    plot.currentPen.getOrElse(
      throw new EngineException(
        context, this, "Plot '" + plot.name + "' has no pens!"))
  }
}

abstract class PlotCommand(args: Int*)
extends Command with Helpers {
}

abstract class PlotReporter(returnType: Int, args: Int*)
extends Reporter with Helpers {
}

//
// commands requiring only the plot manager (it's ok if there are no plots)
//

class _clearallplots extends PlotCommand() {
  override def perform(context: Context) {
    plotManager.clearAll()
    context.ip = next
  }
}
class _setupplots extends PlotCommand() {
  override def callsOtherCode = true
  override def perform(context: Context) {
    workspace.setupPlots(context)
    context.ip = next
  }
}
class _updateplots extends PlotCommand() {
  override def callsOtherCode = true
  override def perform(context: Context) {
    workspace.updatePlots(context)
    context.ip = next
  }
}
class _setcurrentplot extends PlotCommand(Syntax.StringType) {
  override def perform(context: Context) {
    val name = argEvalString(context, 0)
    val plot = plotManager.getPlot(name)
    if (plot == null)
      throw new EngineException(context, this,
        "no such plot: \"" + name + "\"")
    plotManager.currentPlot = Some(plot)
    context.ip = next
  }
}

//
// commands requiring that there be a current plot.
//

class _clearplot extends PlotCommand() {
  override def perform(context: Context) {
    currentPlot(context).clear()
    context.ip = next
  }
}
class _autoplotoff extends PlotCommand() {
  override def perform(context: Context) {
    currentPlot(context).state = currentPlot(context).state.copy(autoPlotOn = false)
    context.ip = next
  }
}
class _autoploton extends PlotCommand() {
  override def perform(context: Context) {
    currentPlot(context).state = currentPlot(context).state.copy(autoPlotOn = true)
    context.ip = next
  }
}

class _plot extends PlotCommand(Syntax.NumberType) {
  override def perform(context: Context) {
    val y = argEvalDoubleValue(context, 0)
    currentPlot(context).plot(y)
    context.ip = next
  }
}

class _plotxy extends PlotCommand(Syntax.NumberType, Syntax.NumberType) {
  override def perform(context: Context) {
    val x = argEvalDoubleValue(context, 0)
    val y = argEvalDoubleValue(context, 1)
    currentPlot(context).plot(x, y)
    context.ip = next
  }
}

class _setplotxrange extends PlotCommand(Syntax.NumberType, Syntax.NumberType) {
  override def perform(context: Context) {
    val min = argEvalDoubleValue(context, 0)
    val max = argEvalDoubleValue(context, 1)
    if (min >= max)
      throw new EngineException(context, this,
        "the minimum must be less than the maximum, but " +  min + " is greater than or equal to " + max)
    val plot = currentPlot(context)
    plot.state = plot.state.copy(xMin = min, xMax = max)
    plot.makeDirty()
    context.ip = next
  }
}

class _setplotyrange extends PlotCommand(Syntax.NumberType, Syntax.NumberType) {
  override def perform(context: Context) {
    val min = argEvalDoubleValue(context, 0)
    val max = argEvalDoubleValue(context, 1)
    if (min >= max)
      throw new EngineException(context, this,
        "the minimum must be less than the maximum, but " +  min + " is greater than or equal to " + max)
    val plot = currentPlot(context)
    plot.state = plot.state.copy(yMin = min, yMax = max)
    plot.makeDirty()
    context.ip = next
  }
}

class _createtemporaryplotpen extends PlotCommand(Syntax.StringType) {
  override def perform(context: Context) {
    val name = argEvalString(context, 0)
    val plot = currentPlot(context)
    plot.currentPen = plot.getPen(name).getOrElse(plot.createPlotPen(name, true))
    context.ip = next
  }
}

class _histogram extends PlotCommand(Syntax.ListType) {
  import org.nlogo.api.Dump
  override def perform(context: Context) {
    val list = argEvalList(context, 0)
    val pen = currentPen(context)
    pen.plotListenerReset(false)
    if(pen.interval <= 0)
      throw new EngineException(context, this,
        "You cannot histogram with a plot-pen-interval of " + Dump.number(pen.interval) + ".")
    val plot = currentPlot(context)
    plot.beginHistogram(pen)
    for(d <- list.scalaIterator.collect{case d: java.lang.Double => d.doubleValue})
      plot.nextHistogramValue(d)
    plot.endHistogram(pen)
    plot.makeDirty()
    context.ip = next
  }
}

class _sethistogramnumbars extends PlotCommand(Syntax.NumberType) {
  override def perform(context: Context) {
    val numBars = argEvalIntValue(context, 0)
    if (numBars < 1)
      throw new EngineException(context, this,
        "You cannot make a histogram with " + numBars + " bars.")
    currentPlot(context).setHistogramNumBars(currentPen(context), numBars)
    context.ip = next
  }
}

class _exportplot extends PlotCommand(Syntax.StringType, Syntax.StringType) {
  override def perform(context: Context) {
    val name = argEvalString(context, 0)
    val path = argEvalString(context, 1)
    if (plotManager.getPlot(name) == null) {
      throw new EngineException(context, this, "no such plot: \"" + name + "\"")
    }
    // Workspace.waitFor() switches to the event thread if we're running with a GUI - ST 12/17/04
    workspace.waitFor(new CommandRunnable {
      def run() {
        try workspace.exportPlot(name, workspace.fileManager.attachPrefix(path))
        catch {
          case ex: java.io.IOException =>
            throw new EngineException(context, _exportplot.this, token.text + ": " + ex.getMessage)
        }
      }
    })
    context.ip = next
  }
}

// this also requires only the PlotManager, but it seems better to put it here next to exportplot.
class _exportplots extends PlotCommand(Syntax.StringType) {
  override def perform(context: Context) {
    val path = argEvalString(context, 0)
    if (plotManager.getPlotNames.length == 0)
      throw new EngineException(context, this, "there are no plots to export")
    // Workspace.waitFor() switches to the event thread if we're running with a GUI - ST 12/17/04
    workspace.waitFor(new CommandRunnable {
      def run() {
        try workspace.exportAllPlots(workspace.fileManager.attachPrefix(path))
        catch {
          case ex: java.io.IOException =>
            throw new EngineException(context, _exportplots.this,
              token.text + ": " + ex.getMessage)
        }
      }
    })
    context.ip = next
  }
}

//
// reporters
//

class _autoplot extends PlotReporter(Syntax.BooleanType) {
  override def report(context: Context) =
    Boolean.box(currentPlot(context).autoPlotOn)
}
class _plotname extends PlotReporter(Syntax.StringType) {
  override def report(context: Context) =
    currentPlot(context).name
}
class _plotxmin extends PlotReporter(Syntax.NumberType) {
  override def report(context: Context) =
    Double.box(currentPlot(context).xMin)
}
class _plotxmax extends PlotReporter(Syntax.NumberType) {
  override def report(context: Context) =
    Double.box(currentPlot(context).xMax)
}
class _plotymin extends PlotReporter(Syntax.NumberType) {
  override def report(context: Context) =
    Double.box(currentPlot(context).yMin)
}
class _plotymax extends PlotReporter(Syntax.NumberType) {
  override def report(context: Context) =
    Double.box(currentPlot(context).yMax)
}
class _plotpenexists extends PlotReporter(Syntax.BooleanType, Syntax.StringType) {
  override def report(context: Context) =
    Boolean.box(currentPlot(context).getPen(argEvalString(context, 0)).isDefined)
}

//
// plot pen prims
//

final class _plotpendown extends PlotCommand() {
  override def perform(context: Context) {
    currentPen(context).isDown = true
    context.ip = next
  }
}
final class _plotpenup extends PlotCommand() {
  override def perform(context: Context) {
    currentPen(context).isDown = false
    context.ip = next
  }
}
final class _plotpenshow extends PlotCommand() {
  override def perform(context: Context) {
    currentPen(context).hidden = false
    context.ip = next
  }
}
final class _plotpenhide extends PlotCommand() {
  override def perform(context: Context) {
    currentPen(context).hidden = true
    context.ip = next
  }
}
final class _plotpenreset extends PlotCommand() {
  override def perform(context: Context) {
    currentPen(context).hardReset()
    currentPen(context).plotListenerReset(true)
    currentPlot(context).makeDirty()
    context.ip = next
  }
}

final class _setplotpeninterval extends PlotCommand(Syntax.NumberType) {
  override def perform(context: Context) {
    currentPen(context).interval = argEvalDoubleValue(context, 0)
    context.ip = next
  }
}

final class _setplotpenmode extends PlotCommand(Syntax.NumberType) {
  import org.nlogo.core.PlotPenInterface
  override def perform(context: Context) {
    val mode = argEvalIntValue(context, 0)
    if (mode < PlotPenInterface.MinMode || mode > PlotPenInterface.MaxMode) {
      throw new EngineException(context, this,
        mode + " is not a valid plot pen mode (valid modes are 0, 1, and 2)")
    }
    currentPen(context).mode = mode
    context.ip = next
  }
}

final class _setplotpencolor extends PlotCommand(Syntax.NumberType | Syntax.ListType) {
  import org.nlogo.api.Color
  override def perform(context: Context) {
    val obj = args(0).report(context)
    obj match {
      case rgbList: LogoList =>
        try
          currentPen(context).color = Color.getARGBIntByRGBAList(rgbList)
        catch {
          case e: ClassCastException =>
            throw new org.nlogo.nvm.EngineException(context, this, displayName + " an rgb list must contain only numbers")
        }
      case c: java.lang.Double =>
        currentPen(context).color =
          Color.getARGBbyPremodulatedColorNumber(
            Color.modulateDouble(c))
      case _ => throw new org.nlogo.nvm.ArgumentTypeException(context, this, 0, Syntax.ListType | Syntax.NumberType, obj)
    }
    context.ip = next
  }
}

final class _setcurrentplotpen extends PlotCommand(Syntax.StringType) {
  override def perform(context: Context) {
    val penName = argEvalString(context, 0)
    val plot = currentPlot(context)
    plot.currentPen = plot.getPen(penName).getOrElse(
      throw new EngineException(
        context, this, "There is no pen named \"" + penName + "\" in the current plot"))
    context.ip = next
  }
}
