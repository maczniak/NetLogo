// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.prim.hubnet

import org.nlogo.agent.Observer
import org.nlogo.api.{ CommandRunnable, Dump, Syntax }
import org.nlogo.core.{ AgentKind, LogoList }
import org.nlogo.nvm.{ EngineException, Command, Context, Reporter }
import Syntax._

class _hubnetmessage extends Reporter {
  override def syntax = reporterSyntax(WildcardType)
  override def report(context: Context) =
    workspace.getHubNetManager.getMessage
}

class _hubnetmessagesource extends Reporter {
  override def syntax = reporterSyntax(StringType)
  override def report(context: Context) =
    workspace.getHubNetManager.getMessageSource
}

class _hubnetmessagetag extends Reporter {
  override def syntax = reporterSyntax(StringType)
  override def report(context: Context) =
    workspace.getHubNetManager.getMessageTag
}

class _hubnetmessagewaiting extends Reporter {
  override def syntax = reporterSyntax(BooleanType)
  override def report(context: Context) =
    workspace.getHubNetManager.messageWaiting.asInstanceOf[AnyRef]
}

class _hubnetentermessage extends Reporter {
  override def syntax = reporterSyntax(BooleanType)
  override def report(context: Context) =
    workspace.getHubNetManager.enterMessage.asInstanceOf[AnyRef]
}

class _hubnetexitmessage extends Reporter {
  override def syntax = reporterSyntax(BooleanType)
  override def report(context: Context) =
    workspace.getHubNetManager.exitMessage.asInstanceOf[AnyRef]
}

class _hubnetclientslist extends Reporter {
  override def syntax = reporterSyntax(ListType)
  override def report(context: Context): AnyRef =
    LogoList(workspace.getHubNetManager.clients.toSeq.map(_.asInstanceOf[AnyRef]): _*)
}

class _hubnetkickclient extends Command {
  override def syntax = commandSyntax(Array(StringType), "OTPL")
  override def perform(context: Context) {
    workspace.getHubNetManager.kick(argEvalString(context, 0))
    context.ip = next
  }
}

class _hubnetkickallclients extends Command {
  switches = true

  override def syntax = commandSyntax("OTPL")

  override def perform(context: Context) {
    workspace.getHubNetManager.kickAll()
    context.ip = next
  }
}

class _hubnetinqsize extends Reporter {
  override def syntax = reporterSyntax(NumberType)
  override def report(context: Context) =
    workspace.getHubNetManager.getInQueueSize.toDouble.asInstanceOf[AnyRef]
}

class _hubnetoutqsize extends Reporter {
  override def syntax = reporterSyntax(NumberType)
  override def report(context: Context) =
    workspace.getHubNetManager.getOutQueueSize.toDouble.asInstanceOf[AnyRef]
}

class _hubnetcreateclient extends Command {
  override def syntax = commandSyntax("O---")
  override def perform(context: Context) {
    workspace.getHubNetManager.newClient(false,0)
    context.ip = next
  }
}

class _hubnetsendfromlocalclient extends Command {
  override def syntax = commandSyntax(Array(StringType, StringType, WildcardType))
  override def perform(context: Context) {
    val clientId = argEvalString(context, 0)
    val messageTag = argEvalString(context, 1)
    val payload = args(2).report(context)
    // todo: check if we got an error back here!
    workspace.getHubNetManager.sendFromLocalClient(clientId, messageTag, payload)
    context.ip = next
  }
}

class _hubnetwaitforclients extends Command {
  // two args:
  //   1) number of clients to wait for.
  //   2) timeout (milliseconds)
  override def syntax = commandSyntax(Array(NumberType, NumberType))
  override def perform(context: Context) {
    val numClients = argEvalDoubleValue(context, 0).toInt
    val timeout = argEvalDoubleValue(context, 1).toLong
    val (ok, numConnected) =
      workspace.getHubNetManager.waitForClients(numClients, timeout)
    if(! ok)
      throw new EngineException(context, this,
        "waited " + timeout + "ms for " + numClients +
                " clients, but only got " + numConnected)
    context.ip = next
  }
}

class _hubnetwaitformessages extends Command {
  // two args:
  //   1) number of messages to wait for.
  //   2) timeout (milliseconds)
  override def syntax = commandSyntax(Array(NumberType, NumberType))
  override def perform(context: Context) {
    val numMessages = argEvalDoubleValue(context, 0).toInt
    val timeout = argEvalDoubleValue(context, 1).toLong
    val (ok, numReceived) =
      workspace.getHubNetManager.waitForMessages(numMessages, timeout)
    if(! ok)
      throw new EngineException(context, this,
        "waited " + timeout + "ms for " + numMessages +
                " messages, but only got " + numReceived)
    context.ip = next
  }
}

class _hubnetsetviewmirroring extends Command {
  override def syntax = commandSyntax(Array(BooleanType))
  override def perform(context: Context) {
    workspace.getHubNetManager.setViewMirroring(argEvalBooleanValue(context, 0))
    context.ip = next
  }
}

class _hubnetsetplotmirroring extends Command {
  override def syntax = commandSyntax(Array(BooleanType))
  override def perform(context: Context) {
    workspace.getHubNetManager.setPlotMirroring(argEvalBooleanValue(context, 0))
    context.ip = next
  }
}

class _hubnetsetclientinterface extends Command {
  def syntax = commandSyntax(Array[Int](StringType, ListType), "O---")
  def perform(context: Context) {
    val interfaceType = argEvalString(context, 0)
    val interfaceInfo = argEvalList(context, 1)
    workspace.waitFor(new CommandRunnable {
      override def run() {
        val serializableInfo = interfaceInfo.toVector.collect {
          case able: java.io.Serializable => able
        }.toSeq
        workspace.getHubNetManager.setClientInterface(interfaceType, serializableInfo)
      }
    })
    context.ip = next
  }
}

class _hubnetfetchmessage extends Command {
  override def syntax =
    commandSyntax
  override def perform(context: Context) {
    workspace.getHubNetManager.fetchMessage()
    context.ip = next
  }
}

class _hubnetreset extends Command {
  override def syntax =
    commandSyntax("O---")
  override def perform(context: Context) {
    workspace.waitFor(
      new CommandRunnable {
        override def run() {
          workspace.getHubNetManager.reset()
        }})
    context.ip = next
  }
}

class _hubnetresetperspective extends Command {
  override def syntax =
    commandSyntax(Array(StringType), "OTPL")
  override def perform(context: Context) {
    val client = argEvalString(context, 0)
    val agent = world.observer.targetAgent
    val agentKind =
      Option(agent).map(_.kind).getOrElse(AgentKind.Observer)
    val id = Option(agent).map(_.id).getOrElse(0L)
    workspace.waitFor(
      new CommandRunnable {
        override def run() {
          workspace.getHubNetManager.sendAgentPerspective(
            client, world.observer.perspective.export,
            agentKind, id, (world.worldWidth() - 1) / 2, true)
        }})
    context.ip = next
  }
}

class _hubnetbroadcast extends Command {
  override def syntax =
    commandSyntax(Array(StringType, WildcardType))
  override def perform(context: Context) {
    val variableName = argEvalString(context, 0)
    val data = args(1).report(context)
    workspace.getHubNetManager.broadcast(variableName, data)
    context.ip = next
  }
}

class _hubnetbroadcastclearoutput extends Command {
  override def syntax =
    commandSyntax
  override def perform(context: Context) {
    workspace.getHubNetManager.broadcastClearText()
    context.ip = next
  }
}

class _hubnetbroadcastmessage extends Command {
  override def syntax =
    commandSyntax(Array(WildcardType))
  override def perform(context: Context) {
    val data = args(0).report(context)
    workspace.getHubNetManager.broadcast(Dump.logoObject(data) + "\n")
    context.ip = next
  }
}

class _hubnetbroadcastusermessage extends Command {
  override def syntax =
    commandSyntax(Array(WildcardType))
  override def perform(context: Context) {
    val data = args(0).report(context)
    workspace.getHubNetManager.broadcastUserMessage(Dump.logoObject(data))
    context.ip = next
  }
}

class _hubnetroboclient extends Command {
  override def syntax =
    commandSyntax(Array(NumberType), "O---")
  override def perform(context: Context) {
    workspace.getHubNetManager.newClient(true, argEvalIntValue(context, 0))
    context.ip = next
  }
}

class _hubnetclearoverrides extends Command {
  override def syntax =
    commandSyntax(Array(StringType), "OTPL")
  override def perform(context: Context) {
    val client = argEvalString(context, 0)
    workspace.waitFor(
      new CommandRunnable {
        override def run() {
          workspace.getHubNetManager.clearOverrideLists(client)
        }})
    context.ip = next
  }
}

class _hubnetclearoverride extends Command {
  override def syntax =
    commandSyntax(Array(StringType,
                        AgentsetType | AgentType,
                        StringType),
                  "OTPL", "?")
  override def perform(context: Context) {
    import org.nlogo.agent.{ Agent, AgentSet, ArrayAgentSet }
    val client = argEvalString(context, 0)
    val target = args(1).report(context)
    val varName = argEvalString(context, 2)
    val set = target match {
      case agent: Agent =>
        val set = new ArrayAgentSet(agent.kind, 1, false)
        set.add(agent)
        set
      case set: AgentSet =>
        set
    }
    if(!workspace.getHubNetManager.isOverridable(set.kind, varName))
      throw new EngineException(context, this,
        "you cannot override " + varName)
    val overrides = new collection.mutable.ArrayBuffer[java.lang.Long](set.count)
    val iter = set.iterator
    while(iter.hasNext)
      overrides += Long.box(iter.next().id)
    workspace.waitFor(
      new CommandRunnable() {
        override def run() {
          workspace.getHubNetManager.clearOverride(
            client, set.kind, varName, overrides)}})
    context.ip = next
  }
}
