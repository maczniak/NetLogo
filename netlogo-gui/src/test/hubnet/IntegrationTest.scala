// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.hubnet

import server.HeadlessHubNetManager
import client.{ Client, ClientPanel, ErrorHandler }
import org.nlogo.core.Femto
import org.nlogo.headless.HeadlessWorkspace
import org.nlogo.window.EditorFactory
import org.nlogo.nvm.{ CompilerInterface, DefaultCompilerServices }

import org.scalatest.{ BeforeAndAfter, FunSuite, OneInstancePerTest }

class IntegrationTest extends FunSuite with OneInstancePerTest with BeforeAndAfter {
  val workspace = HeadlessWorkspace.newInstance
  val hnManager = new HeadlessHubNetManager(workspace)

  val errorHandler = new ErrorHandler {
    def handleLoginFailure(errorMessage: String) = {}
    def handleDisconnect(activityName: String, connected: Boolean, reason: String) = {}
    def completeLogin() = {}
  }

  val editorFactory = new EditorFactory {
    def newEditor(cols: Int, rows: Int, disableFocusTraversal: Boolean): org.nlogo.editor.AbstractEditorArea = null
  }

  val compiler = new DefaultCompilerServices(Femto.get[CompilerInterface]("org.nlogo.compiler.Compiler", org.nlogo.api.NetLogoLegacyDialect))

  before {
    hnManager.reset()
  }

  after {
    hnManager.disconnect()
    hnManager.connectionManager.removeAllClients()
    workspace.dispose
  }

  class ClientTestThread(port: Int, i: Int) extends Thread("Test thread: " + i) {
    val client = new Client(i.toString)
    val exc = client.login("foo" + i, "127.0.0.1", port)
    Thread.sleep((scala.util.Random.nextInt % 10).abs)
    client.logout()
  }

  test("basic login") {
    assert(hnManager.connectionManager.clients.isEmpty)
    val threads = for (i <- 1 to 100) yield
      new ClientTestThread(hnManager.connectionManager.port, i)
    threads.foreach(_.start())
    threads.foreach(_.join())
    assert(! hnManager.connectionManager.clients.values.exists(_.clientId == null))
    assert(hnManager.connectionManager.clients.size == 0)
  }
}
