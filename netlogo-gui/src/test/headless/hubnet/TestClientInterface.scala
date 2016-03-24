// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.headless.hubnet

import org.nlogo.api.{ FileIO, LocalFile, ModelSection, ModelReader}
import org.nlogo.util.TestUtils._
import org.nlogo.headless.TestUsingWorkspace
import org.nlogo.hubnet.protocol.ClientInterface

class TestClientInterface extends TestUsingWorkspace {

  testUsingWorkspace("empty ClientInterface is serializable"){ workspace =>
    val ci = new ClientInterface(Nil, Nil, Nil, Nil, workspace)
    assert(ci.toString === roundTripSerialization(ci).toString)
  }

  testUsingWorkspace("legit ClientInterface is serialiazble"){ workspace =>
    import collection.JavaConverters._
    val model = "test/hubnet/client-interface.nlogo"
    val unparsedWidgets = getClientWidgets(model)
    val parsedWidgets = ModelReader.parseWidgets(unparsedWidgets).asScala.map(_.asScala.toList).toList
    val ci = new ClientInterface(parsedWidgets, unparsedWidgets.toList,
                                 workspace.world.turtleShapeList.shapes,
                                 workspace.world.linkShapeList.shapes,
                                 workspace)
    assert(ci.toString === roundTripSerialization(ci).toString)
  }

  private def getClientWidgets(modelFilePath: String) = {
    ModelReader.parseModel(FileIO.file2String(modelFilePath)).get(ModelSection.HubNetClient)
  }

  test("test roundTripSerialization method"){
    assert("s" == roundTripSerialization("s"))
    assert(7.asInstanceOf[AnyRef] == roundTripSerialization(7))
  }
}