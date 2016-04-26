// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.hubnet.client

import java.net.{ Socket, SocketTimeoutException, ServerSocket }
import java.io.{ ObjectInputStream, ObjectOutputStream }

import org.nlogo.api.Version

import org.scalatest.{ BeforeAndAfter, FunSuite, OneInstancePerTest }

import scala.io.Source
import scala.concurrent.Channel

class ClientTest extends FunSuite with BeforeAndAfter with OneInstancePerTest {

  val testPort = 8098

  class ServerThread extends Thread("Client test thread") {
    var isRunning = true

    def outboundMessages = new Channel[AnyRef]()
    def inboundMessages = new Channel[AnyRef]()

    var serverSocket = Option.empty[ServerSocket]

    protected def acceptConnection(ss: ServerSocket): (Socket, ObjectInputStream, ObjectOutputStream) = {
      val sock = ss.accept()
      val istream = new ObjectInputStream(sock.getInputStream)
      val ostream = new ObjectOutputStream(sock.getOutputStream)
      (sock, istream, ostream)
    }

    protected def initializeServerSocket(): ServerSocket = {
      val ss = new ServerSocket(testPort)
      ss.setSoTimeout(100)
      serverSocket = Some(ss)
      ss
    }

    override def run(): Unit = {
      val ss = initializeServerSocket()

      while (! Thread.interrupted() && isRunning) {
        try {
          val (sock, istream, ostream) = acceptConnection(ss)

          try {
            while(isRunning) {
              val inObj = istream.readObject()
              println("RECEIVED MESSAGE: ")
              println(inObj)
              inboundMessages.write(inObj)
              ostream.writeObject(outboundMessages.read)
            }
          } finally {
            istream.close()
            ostream.close()
            sock.close()
          }
        } catch {
            case i: InterruptedException =>
              println("INTERRUPTED: " + Thread.interrupted)
              isRunning = false
            case _: SocketTimeoutException => // handled in finally
            case e: Exception =>
              println(s"exception in server thread: ${e} - ${e.getMessage}")
              isRunning = false
              throw e
        }
      }

      ss.close()
    }
  }

  val serverThread = new ServerThread()

  before {
    serverThread.start()
  }

  after {
    serverThread.interrupt()
    println("CALLED INTERRUPT")
    serverThread.join()
  }

  test("client starts in state Initial") {
    val client = new Client("foo")
    assertResult(ConnectionState.Initial.id)(client.currentState.get)
  }

  test("when client attempts to login to a nonexistent server, it fails with error") {
    val client = new Client("foo")
    val login = client.login("foo", "0.0.0.0", 1)
    assert(login.map(_.startsWith("Login failed:")).getOrElse(false))
  }

  test("when the client first connects to a server, it sends its version") {
    val client = new Client("foo")
    serverThread.outboundMessages.write(Version.version)
    val login = client.login("foo", "127.0.0.1", testPort)
    client.logout()
    // val transmitted = serverThread.inboundMessages.read
    // assertResult(Version.version)(transmitted)
  }
}
