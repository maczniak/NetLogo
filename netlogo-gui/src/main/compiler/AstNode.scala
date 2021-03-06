// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.compiler

import org.nlogo.{ core, nvm }

/**
 * An interface representing a node in the NetLogo abstract syntax tree (AKA parse tree, in
 * NetLogo's case).
 *
 * Each AstNode, even if synthesized, should correspond to some particular source fragment, as
 * indicated by the position and length. It's the compiler's job to make sure these values are
 * always reasonable.
 */
trait AstNode extends core.AstNode {
  def start: Int
  def end: Int
  def file: String
  def accept(v: AstVisitor)
}

/**
 * an interface for AST tree-walkers. This represents the usual Visitor
 * pattern with double-dispatch.
 */
trait AstVisitor {
  def visitProcedureDefinition(proc: ProcedureDefinition)
  def visitCommandBlock(block: CommandBlock)
  def visitReporterApp(app: ReporterApp)
  def visitReporterBlock(block: ReporterBlock)
  def visitStatement(stmt: Statement)
  def visitStatements(stmts: Statements)
}

/**
 * The default AST tree-walker. This simply visits each node of the
 * tree, and visits any children of each node in turn. Subclasses can
 * implement pre-order or post-order traversal, or a wide range of other
 * strategies.
 */
class DefaultAstVisitor extends AstVisitor {
  def visitProcedureDefinition(proc: ProcedureDefinition) { proc.statements.accept(this) }
  def visitCommandBlock(block: CommandBlock) { block.statements.accept(this) }
  def visitReporterApp(app: ReporterApp) { app.args.foreach(_.accept(this)) }
  def visitReporterBlock(block: ReporterBlock) { block.app.accept(this) }
  def visitStatement(stmt: Statement) { stmt.args.foreach(_.accept(this)) }
  def visitStatements(stmts: Statements) { stmts.stmts.foreach(_.accept(this)) }
}

/**
 * represents a NetLogo expression. An expression is either a block or a
 * reporter application (variable references and constants (including lists),
 * are turned into reporter applications).
 */
trait Expression extends AstNode {
  /**
   * returns the type of this expression. Generally synthesized from
   * types of subexpressions.
   */
  def reportedType(): Int
  def start_=(start: Int)
  def end_=(end: Int)
}

/**
 * represents an application, in the abstract (either a reporter application
 * of a command application). This is used when parsing arguments, when we
 * don't care what kind of application the args are for.
 */
trait Application extends AstNode {
  def args: Seq[Expression]
  def coreInstruction: core.Instruction
  def nvmInstruction: nvm.Instruction
  def end_=(end: Int)
  def addArgument(arg: Expression)
  def replaceArg(index: Int, expr: Expression)
}

/**
 * represents a single procedure definition.  really just a container
 * for the procedure body, which is a Statements object.
 */
class ProcedureDefinition(val procedure: nvm.Procedure, val statements: Statements) extends AstNode {
  def start = procedure.pos
  def end = procedure.end
  def file = procedure.filename
  def accept(v: AstVisitor) { v.visitProcedureDefinition(this) }
}

/**
 * represents a chunk of zero or more NetLogo statements. Note that this is
 * not necessarily a "block" of statements, as block means something specific
 * (enclosed in [], in particular). This class is used to represent other
 * groups of statements as well, for instance procedure bodies.
 */
class Statements(val file: String) extends AstNode {
  var start: Int = _
  var end: Int = _
  /**
   * a List of the actual Statement objects.
   */
  private val _stmts = collection.mutable.Buffer[Statement]()
  def stmts: Seq[Statement] = _stmts
  def body: Seq[Statement] = _stmts
  def addStatement(stmt: Statement) {
    _stmts.append(stmt)
    recomputeStartAndEnd()
  }
  private def recomputeStartAndEnd() {
    if (stmts.isEmpty) { start = 0; end = 0 }
    else { start = stmts(0).start; end = stmts(stmts.size - 1).end }
  }
  override def toString = stmts.mkString(" ")
  def accept(v: AstVisitor) { v.visitStatements(this) }
}

/**
 * represents a NetLogo statement. Statements only have one form: command
 * application.
 */
class Statement(var coreCommand: core.Command, var command: nvm.Command, var start: Int, var end: Int, val file: String)
    extends Application {
  private val _args = collection.mutable.Buffer[Expression]()
  override def args: Seq[Expression] = _args
  def nvmInstruction = command // for Application
  def coreInstruction = coreCommand // for Application
  def addArgument(arg: Expression) { _args.append(arg) }
  override def toString = command.toString + "[" + args.mkString(", ") + "]"
  def accept(v: AstVisitor) { v.visitStatement(this) }
  def replaceArg(index: Int, expr: Expression) { _args(index) = expr }

  def removeArgument(index: Int) { _args.remove(index) }
}

/**
 * represents a block containing zero or more statements. Called a command
 * block rather than a statement block for consistency with usual NetLogo
 * jargon. Note that this is an Expression, and as such can be an argument
 * to commands and reporters, etc.
 */
class CommandBlock(val statements: Statements, var start: Int, var end: Int, val file: String) extends Expression {
  def reportedType() = core.Syntax.CommandBlockType
  override def toString = "[" + statements.toString + "]"
  def accept(v: AstVisitor) { v.visitCommandBlock(this) }
}

/**
 * represents a set of code that should not be evaluated, but can be lexed
 * and tokenized.  This is an expression, and can be used as an argument
 * to commands and reports.
 */
class CodeBlock(val code: String, var start: Int, var end: Int, val file: String) extends Expression {
  def reportedType() = core.Syntax.CodeBlockType
  override def toString = "[" + code + "]"
  def accept(v: AstVisitor) {}
}

/**
 * represents a block containing exactly one expression. Called a reporter
 * block rather than an expression block for consistency with usual NetLogo
 * jargon. Note that this is an Expression, and as such can be an argument
 * to commands and reporters, etc. However, it is a different expression from
 * the expression it contains... Its "blockness" is significant.
 */
class ReporterBlock(val app: ReporterApp, var start: Int, var end: Int, val file: String) extends Expression {
  override def toString = "[" + app.toString() + "]"
  def accept(v: AstVisitor) { v.visitReporterBlock(this) }
  /**
   * computes the type of this block. Reporter block types are
   * determined in a somewhat complicated way. This is derived from
   * code from the old parser.
   */
  def reportedType(): Int = {
    val appType = app.reportedType
    import core.Syntax._
    appType match {
      case BooleanType => BooleanBlockType
      case NumberType => NumberBlockType
      case _ =>
        if (compatible(appType, BooleanType)
            || compatible(appType, NumberType))
          ReporterBlockType
        else OtherBlockType
    }
  }
}

/**
 * represents a reporter application. This is the typical kind of NetLogo
 * expression, things like "round 5" and "3 + 4". However, this class also
 * represents things like constants, which are converted into no-arg reporter
 * applications as they're parsed.
 */
class ReporterApp(var coreReporter: core.Reporter, var reporter: nvm.Reporter, var start: Int, var end: Int, val file: String)
extends Expression with Application {
  /**
   * the args for this application.
   */
  private val _args = collection.mutable.Buffer[Expression]()
  override def args: Seq[Expression] = _args
  def coreInstruction = coreReporter // for Application
  def nvmInstruction = reporter // for Application
  def addArgument(arg: Expression) { _args.append(arg) }
  def reportedType() = coreReporter.syntax.ret
  def accept(v: AstVisitor) { v.visitReporterApp(this) }
  def removeArgument(index: Int) { _args.remove(index) }
  def replaceArg(index: Int, expr: Expression) { _args(index) = expr }
  def clearArgs() { _args.clear() }
  override def toString = reporter.toString + "[" + args.mkString(", ") + "]"
}
