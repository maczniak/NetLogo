package org.nlogo.app.previewcommands

import org.nlogo.workspace.{ PreviewCommandsRunner, WorkspaceFactory }
import scala.Left
import scala.Right
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.nlogo.core.{ CompilerException, Model }
import org.nlogo.api.PreviewCommands
import org.nlogo.api.PreviewCommands.Compilable
import org.nlogo.swing.HasPropertyChangeSupport

class GUIState(
  model: Model,
  modelPath: String,
  workspaceFactory: WorkspaceFactory)
  extends HasPropertyChangeSupport {

  private var _previewCommands: Option[PreviewCommands] = None
  private var _previewCommandsRunner: Option[Either[CompilerException, PreviewCommandsRunner]] = None

  def previewCommands: Option[PreviewCommands] =
    _previewCommands
  def previewCommandsRunnable: Option[PreviewCommandsRunner#Runnable] =
    _previewCommandsRunner.flatMap(_.right.toOption.map(_.runnable))
  def compilerException: Option[CompilerException] =
    _previewCommandsRunner.flatMap(_.left.toOption)

  def previewCommands_=(newPreviewCommands: PreviewCommands): Unit = {
    val oldPreviewCommands = previewCommands
    val oldCompilerException = compilerException
    _previewCommands = Some(newPreviewCommands)
    _previewCommandsRunner = newPreviewCommands match {
      case compilableCommands: Compilable =>
        Try(PreviewCommandsRunner.fromModelContents(
          workspaceFactory, model, modelPath, newPreviewCommands
        )) match {
          case Success(runner)               => Some(Right(runner))
          case Failure(e: CompilerException) => Some(Left(e))
          case Failure(e)                    => throw e
        }
      case _ => None
    }
    propertyChangeSupport.firePropertyChange("previewCommands", oldPreviewCommands, previewCommands)
    propertyChangeSupport.firePropertyChange("compilerException", oldCompilerException, compilerException)
  }

}
