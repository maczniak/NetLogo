// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.hubnet.client

import org.nlogo.api.Version

import org.nlogo.hubnet.connection.{ Streamable, ConnectionTypes, AbstractConnection}
import org.nlogo.hubnet.protocol._

import java.util.concurrent.atomic.AtomicInteger
import java.io.IOException
import java.net.{Socket, ConnectException, UnknownHostException, NoRouteToHostException}

object ConnectionState extends Enumeration {
  type ConnectionState = Value
  val Initial, VersionSent, HandshakeSent, Connected, LoggedOut = Value
}

class Client(uid: String) {
  import ConnectionState._
  private var userid: String = null
  private var hostip: String = null
  private var port: Int = 0

  private var listener: Listener = null

  val currentState = new AtomicInteger(ConnectionState.Initial.id)

  def login(userid: String, hostip: String, port: Int): Option[String] = {
    this.userid = userid
    this.hostip = hostip
    this.port = port

    try {
      val socket = new java.net.Socket(hostip, port)
      socket.setSoTimeout(0)
      listener = new Listener(userid, socket)
      listener.start()
      currentState.compareAndSet(Initial.id, VersionSent.id)
      sendDataAndWait(Version.version)
      None
    } catch {
      case e: NoRouteToHostException => Some("Login failed:\n" + hostip + " could not be reached.")
      case e: UnknownHostException   => Some("Login failed:\n" + hostip + " does not resolve to a valid IP address.")
      case e: ConnectException       => Some("Login failed:\n" + "There was no server running at " + hostip + " on port " + port)
      case e: Throwable              => Some("Login failed:\nUnknown cause:\n" + org.nlogo.util.Utils.getStackTrace(e))
    }
  }

  def logout(): Unit = {
    val activeState = currentState.get
    if (currentState.compareAndSet(Connected.id, LoggedOut.id) ||
      currentState.compareAndSet(HandshakeSent.id, LoggedOut.id)) {
        println(s"$uid sending exit from state $activeState")
        listener.stopWriting()
        sendDataAndWait(ExitMessage("Client Exited"))
    } else if (currentState.compareAndSet(VersionSent.id, LoggedOut.id)) {
      // this case is when a version has been sent but no handshake has taken place
      // we send the "KICKME" message to tell the server to boot us
      println(s"$uid sending KICKME")
      listener.stopWriting()
      sendDataAndWait("KICKME")
    } else {
      println(s"$uid asked to logout from state $activeState, no action necessary")
    }
  }

  def disconnect(reason:String) {
    if (listener != null) listener.disconnect(reason)
  }

  def sendDataAndWait(obj: AnyRef): Unit = {
    if (listener != null) {
      try listener.waitForSendData(obj)
      catch { case e: IOException => org.nlogo.util.Exceptions.warn(e) }
    } else System.err.println("Attempted to send data on a shutdown listener, ignoring.")
  }

  def completeLogin(handshake: HandshakeFromServer) {
    if (currentState.compareAndSet(HandshakeSent.id, Connected.id)) {
      println(s"$uid attempted to complete login in state: ${currentState.get}")
      sendDataAndWait(EnterMessage)
      println(s"logged in $uid")
    }
  }

  def handleLoginFailure(errorMessage: String): Unit = {
    currentState.compareAndSet(HandshakeSent.id, LoggedOut.id)
    listener.disconnect(errorMessage)
  }

  def handleProtocolMessage(message: org.nlogo.hubnet.protocol.Message) {
    message match {
      case h: HandshakeFromServer => completeLogin(h)
      case LoginFailure(content) => handleLoginFailure(content)
      case ExitMessage(reason) => disconnect(reason)
      /*
      case WidgetControl(content, tag) => handleWidgetControlMessage(content, tag)
      case DisableView => setDisplayOn(false)
      case ViewUpdate(worldData) => viewWidget.updateDisplay(worldData)
      case PlotControl(content, plotName) => handlePlotControlMessage(content, plotName)
      case PlotUpdate(plot) => handlePlotUpdate(plot)
      case OverrideMessage(data, clear) => viewWidget.handleOverrideList(data.asInstanceOf[OverrideList], clear)
      case ClearOverrideMessage => viewWidget.clearOverrides()
      case AgentPerspectiveMessage(bytes) => viewWidget.handleAgentPerspective(bytes)
      case Text(content, messageType) => messageType match {
        case Text.MessageType.TEXT => clientGUI.addMessage(content.toString)
        case Text.MessageType.USER =>
          OptionDialog.show(getFrame(this), "User Message", content.toString,
            Array(I18N.gui.get("common.buttons.ok"), I18N.gui.get("common.buttons.halt")))
        case Text.MessageType.CLEAR => clientGUI.clearMessages()
      }
      */
        case _ => // ignore everything else, for now
    }
  }

  private def receiveData(a: Any): Unit = {
    a match {
      case m: Message => handleProtocolMessage(m)
      case info: String => if (currentState.get == VersionSent.id) {
        if (info == Version.version) {
          currentState.compareAndSet(VersionSent.id, HandshakeSent.id)
          sendDataAndWait(new HandshakeFromClient(listener.clientId, ConnectionTypes.COMP_CONNECTION))
        } else {
          sendDataAndWait(ExitMessage("Invalid version"))
          handleLoginFailure("The version of the HubNet Client" +
                " you are using does not match the version of the " +
                "server. Please use the HubNet Client that comes with " + info)
        }
      }
    }
  }

  def handleException(e: Exception, sendingEx: Boolean): Unit = {
    listener.stopWriting()
    sendDataAndWait(ExitMessage("Client Exited"))
    currentState.set(LoggedOut.id)
  }

  private class Listener(userName: String, socket: Socket)
    extends AbstractConnection("Listener: " + userName, Streamable(socket)) {

    var clientId = userName

    override def receiveData(data:AnyRef) {
      Client.this.receiveData(data.asInstanceOf[AnyRef])
    }

    override def handleEx(e:Exception, sendingEx: Boolean) {
      println("CLIENT RECEIVED EXCEPTION: " + e + ": " + e.getMessage)
      Client.this.handleException(e, sendingEx)
    }
  }
}
