// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.compiler

import org.scalatest.FunSuite
import org.nlogo.api.DummyExtensionManager
import org.nlogo.core.Program
import org.nlogo.nvm.Procedure

class OptimizerTests extends FunSuite {
  def compileReporter(source:String) =
    compile("globals [glob1] breed [frogs frog] to-report __test [x] report " + source + "\nend")
      .statements.body.head.args.head.toString
  def compileCommands(source:String) =
    compile("globals [glob1] breed [frogs frog] to __test [x] " + source + "\nend")
      .statements.body.head.toString
  private def compile(source:String):ProcedureDefinition = {
    val program = Program.empty()
    val procs = TestHelper.compiledProcedures(source, program)
    assertResult(1)(procs.size)
    val procdef = procs.head
    procdef.accept(new ConstantFolder)
    procdef.accept(new Optimizer(false))
    procdef
  }
  test("testForward1") { assertResult("_fd1[]")(compileCommands("fd 1")) }
  test("testForward2") { assertResult("_jump[_constdouble:0.1[]]")(compileCommands("fd 0.1")) }
  test("testForward3") { assertResult("_jump[_constdouble:-0.1[]]")(compileCommands("fd -0.1")) }
  test("testForward4") { assertResult("_fd[_constdouble:2.0[]]")(compileCommands("fd 2")) }

  test("testPatchAt1") {
    assertResult("_patchhereinternal[]")(compileReporter("patch-at 0 0"))
  }
  test("testPatchAt2") {
    assertResult("_patcheast[]")(compileReporter("patch-at 1 0"))
  }
  test("testPatchAt3") {
    assertResult("_patchsw[]")(compileReporter("patch-at -1 -1"))
  }

  test("testPatchColumn1") {
    assertResult("_patchcol[_constdouble:5.0[]]")(
      compileReporter("patches with [pxcor = 5]"))
  }
  test("testPatchColumn2") {
    assertResult("_patchcol[_constdouble:5.0[]]")(
      compileReporter("patches with [5 = pxcor]"))
  }
  test("testPatchColumn3") {
    assertResult("_patchcol[_observervariable:0[]]")(
      compileReporter("patches with [pxcor = glob1]"))
  }
  test("testPatchColumn4") {
    assertResult("_patchcol[_procedurevariable:X[]]")(
      compileReporter("patches with [pxcor = x]"))
  }

  test("testPatchRow1") {
    assertResult("_patchrow[_constdouble:6.0[]]")(
      compileReporter("patches with [pycor = 6]"))
  }
  test("testPatchRow2") {
    assertResult("_patchrow[_constdouble:6.0[]]")(
      compileReporter("patches with [6 = pycor]"))
  }
  test("testPatchRow3") {
    assertResult("_patchrow[_observervariable:0[]]")(
      compileReporter("patches with [pycor = glob1]"))
  }
  test("testPatchRow4") {
    assertResult("_patchrow[_procedurevariable:X[]]")(
      compileReporter("patches with [pycor = x]"))
  }

  test("testFastHatch1") {
    assertResult("_fasthatch:[_constdouble:5.0[]]")(
      compileCommands("hatch 5"))
  }
  test("testFastHatch2") {
    assertResult("_fasthatch:FROGS[_constdouble:5.0[]]")(
      compileCommands("hatch-frogs 5"))
  }
  test("testFastSprout1") {
    assertResult("_fastsprout:[_constdouble:5.0[]]")(
      compileCommands("sprout 5"))
  }
  test("testFastSprout2") {
    assertResult("_fastsprout:FROGS[_constdouble:5.0[]]")(
      compileCommands("sprout-frogs 5"))
  }
  test("testFastCrt1") {
    assertResult("_fastcreateturtles:[_constdouble:5.0[]]")(
      compileCommands("crt 5"))
  }
  test("testFastCrt2") {
    assertResult("_fastcreateturtles:FROGS[_constdouble:5.0[]]")(
      compileCommands("create-frogs 5"))
  }
  test("testFastCro1") {
    assertResult("_fastcreateorderedturtles:[_constdouble:5.0[]]")(
      compileCommands("cro 5"))
  }
  test("testFastCro2") {
    assertResult("_fastcreateorderedturtles:FROGS[_constdouble:5.0[]]")(
      compileCommands("create-ordered-frogs 5"))
  }
  test("countWith1") {
    assertResult("_countwith[_turtles[], [_constboolean:true[]]]")(
      compileReporter("count turtles with [true]"))
  }
  test("countWith2") {
    assertResult("_countwith[_patches[], [_equal[_patchvariable:2[], _constdouble:15.0[]]]]")(
      compileReporter("count patches with [pcolor = red]"))
  }
  test("oneOfWith") {
    assertResult("_oneofwith[_patches[], [_equal[_patchvariable:2[], _constdouble:15.0[]]]]")(
      compileReporter("one-of patches with [pcolor = red]"))
  }
  test("anyWith1") {
    assertResult("_not[_anywith[_patches[], [_constboolean:true[]]]]")(
      compileReporter("count patches with [true] = 0"))
  }
  test("anyWith2") {
    assertResult("_not[_anywith[_patches[], [_constboolean:true[]]]]")(
      compileReporter("0 = count patches with [true]"))
  }
  test("anyWith3") {
    assertResult("_anywith[_patches[], [_constboolean:true[]]]")(
      compileReporter("0 < count patches with [true]"))
  }
  test("anyWith4") {
    assertResult("_anywith[_patches[], [_constboolean:true[]]]")(
      compileReporter("count patches with [true] > 0"))
  }
  test("anyWith5") {
    assertResult("_anywith[_patches[], [_constboolean:true[]]]")(
      compileReporter("count patches with [true] != 0"))
  }
  test("anyWith6") {
    assertResult("_anywith[_patches[], [_constboolean:true[]]]")(
      compileReporter("0 != count patches with [true]"))
  }
  test("anyWith7") {
    assertResult("_anywith[_patches[], [_constboolean:true[]]]")(
      compileReporter("any? patches with [true]"))
  }
  test("patchVariable1") {
    assertResult("_of[[_patchvariable:3[]], _patches[]]")(
      compileReporter("[plabel] of patches"))
  }
  test("patchVariable2") {
    assertResult("_of[[_patchvariabledouble:0[]], _patches[]]")(
      compileReporter("[pxcor] of patches"))
  }
  test("turtleVariable1") {
    assertResult("_of[[_turtlevariable:12[]], _turtles[]]")(
      compileReporter("[pen-mode] of turtles"))
  }
  test("turtleVariable2") {
    assertResult("_of[[_turtlevariabledouble:3[]], _turtles[]]")(
      compileReporter("[xcor] of turtles"))
  }
  test("randomConst1") {
    assertResult("_randomconst:10[]")(
      compileReporter("random 10"))
  }
  test("randomConst2") {
    assertResult("_random[_constdouble:10.5[]]")(
      compileReporter("random 10.5"))
  }
  test("randomConst3") {
    assertResult("_random[_constdouble:0.0[]]")(
      compileReporter("random 0"))
  }
  test("randomConst4") {
    assertResult("_random[_constdouble:-5.0[]]")(
      compileReporter("random -5"))
  }
  test("otherWith1") {
    assertResult("_otherwith[_turtles[], [_constboolean:true[]]]")(
      compileReporter("other (turtles with [true])"))
  }
  test("otherWith2") {
    assertResult("_otherwith[_turtles[], [_constboolean:true[]]]")(
      compileReporter("(other turtles) with [true]"))
  }
  test("anyOther") {
    assertResult("_anyother[_turtles[]]")(
      compileReporter("any? other turtles"))
  }
  test("anyOtherwith") {
    assertResult("_anyotherwith[_turtles[], [_constboolean:true[]]]")(
      compileReporter("any? other turtles with [true]"))
  }
  test("countOther") {
    assertResult("_countother[_turtles[]]")(
      compileReporter("count other turtles"))
  }
  test("countOtherWith") {
    assertResult("_countotherwith[_turtles[], [_constboolean:true[]]]")(
      compileReporter("count other turtles with [true]"))
  }
}
