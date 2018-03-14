/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package packages


import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.junit.JUnitRunner
import common.{TestHelpers, Wsk, WskProps, WskTestHelpers}
import java.io._

import spray.json.DefaultJsonProtocol.StringJsonFormat
import spray.json.pimpAny

@RunWith(classOf[JUnitRunner])
class CloudantBlueTests extends TestHelpers
    with WskTestHelpers
    with BeforeAndAfterAll {

    implicit val wskprops = WskProps()
    val wsk = new Wsk()

    //set parameters for deploy tests
    val nodejs8folder = "../runtimes/nodejs/actions"
    val nodejs8kind = "nodejs:8"
    val nodejs6folder = "../runtimes/nodejs-6/actions"
    val nodejs6kind = "nodejs:6"
    val phpfolder = "../runtimes/php/actions"
    val phpkind = "php:7.1"
    val pythonfolder = "../runtimes/python/actions"
    val pythonkind = "python-jessie:3"
    val swiftfolder = "../runtimes/swift/actions"
    val swiftkind = "swift:4.1"
    behavior of "Cloudant Trigger Template"

    /**
      * Test the nodejs 8 "cloudant trigger" template
      */
    it should "invoke nodejs 8 process-change.js and get the result" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
      val timestamp: String = System.currentTimeMillis.toString
      val name = "cloudantNode" + timestamp
      val file = Some(new File(nodejs8folder, "process-change.js").toString());
      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, file, kind = Some(nodejs8kind))
      }

      val params = Map("color" -> "Red", "name" -> "Kat").mapValues(_.toJson)

      withActivation(wsk.activation, wsk.action.invoke(name, params)) {
        _.response.result.get.toString should include("A Red cat named Kat was added")
      }
    }

    it should "invoke nodejs 8 process-change.js without parameters and get an error" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
      val timestamp: String = System.currentTimeMillis.toString
      val name = "cloudantNode" + timestamp
      val file = Some(new File(nodejs8folder, "process-change.js").toString());

      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, file, kind = Some(nodejs8kind))
      }

      withActivation(wsk.activation, wsk.action.invoke(name)) {
        activation =>
          activation.response.success shouldBe false
          activation.response.result.get.toString should include("Please make sure name and color are passed in as params.")
      }
    }
    /**
      * Test the nodejs 6 "cloudant trigger" template
      */
    it should "invoke nodejs 6 process-change.js and get the result" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
      val timestamp: String = System.currentTimeMillis.toString
      val name = "cloudantNode6" + timestamp
      val file = Some(new File(nodejs6folder, "process-change.js").toString());
      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, file, kind = Some(nodejs6kind))
      }

      val params = Map("color" -> "Red", "name" -> "Kat").mapValues(_.toJson)

      withActivation(wsk.activation, wsk.action.invoke(name, params)) {
        _.response.result.get.toString should include("A Red cat named Kat was added")
      }
    }

    it should "invoke nodejs 6 process-change.js without parameters and get an error" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
      val timestamp: String = System.currentTimeMillis.toString
      val name = "cloudantNode6" + timestamp
      val file = Some(new File(nodejs6folder, "process-change.js").toString());

      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, file, kind = Some(nodejs6kind))
      }

      withActivation(wsk.activation, wsk.action.invoke(name)) {
        activation =>
          activation.response.success shouldBe false
          activation.response.result.get.toString should include("Please make sure name and color are passed in as params.")
      }
    }

    /**
      * Test the python "cloudant trigger" template
      */
    it should "invoke process-change.py and get the result" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
      val timestamp: String = System.currentTimeMillis.toString
      val name = "cloudantPython" + timestamp
      val file = Some(new File(pythonfolder, "process-change.py").toString());
      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, file, kind = Some(pythonkind))
      }

      val params = Map("color" -> "Red", "name" -> "Kat").mapValues(_.toJson)

      withActivation(wsk.activation, wsk.action.invoke(name, params)) {
        _.response.result.get.toString should include("A Red cat named Kat was added")
      }
    }
    it should "invoke process-change.py without parameters and get an error" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
      val timestamp: String = System.currentTimeMillis.toString
      val name = "cloudantPython" + timestamp
      val file = Some(new File(pythonfolder, "process-change.py").toString());

      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, file, kind = Some(pythonkind))
      }

      withActivation(wsk.activation, wsk.action.invoke(name)) {
        activation =>
          activation.response.success shouldBe false
          activation.response.result.get.toString should include("Please make sure name and color are passed in as params.")
      }
    }

    /**
      * Test the php "cloudant trigger" template
      */
    it should "invoke process-change.php and get the result" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
      val timestamp: String = System.currentTimeMillis.toString
      val name = "cloudantPhp" + timestamp
      val file = Some(new File(phpfolder, "process-change.php").toString());
      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, file, kind = Some(phpkind))
      }

      val params = Map("color" -> "Red", "name" -> "Kat").mapValues(_.toJson)

      withActivation(wsk.activation, wsk.action.invoke(name, params)) {
        _.response.result.get.toString should include("A Red cat named Kat was added")
      }
    }
    it should "invoke process-change.php without parameters and get an error" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
      val timestamp: String = System.currentTimeMillis.toString
      val name = "cloudantPhp" + timestamp
      val file = Some(new File(phpfolder, "process-change.php").toString());

      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, file, kind = Some(phpkind))
      }

      withActivation(wsk.activation, wsk.action.invoke(name)) {
        activation =>
          activation.response.success shouldBe false
          activation.response.result.get.toString should include("Please make sure name and color are passed in as params.")
      }
    }

    /**
      * Test the swift "cloudant trigger" template
      */
    it should "invoke process-change.swift and get the result" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
      val timestamp: String = System.currentTimeMillis.toString
      val name = "cloudantSwift" + timestamp
      val file = Some(new File(swiftfolder, "process-change.swift").toString());
      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, file, kind = Some(swiftkind))
      }

      val params = Map("color" -> "Red", "name" -> "Kat").mapValues(_.toJson)

      withActivation(wsk.activation, wsk.action.invoke(name, params)) {
        _.response.result.get.toString should include("A Red cat named Kat was added")
      }
    }

    it should "invoke process-change.swift without parameters and get an error" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
      val timestamp: String = System.currentTimeMillis.toString
      val name = "cloudantSwift" + timestamp
      val file = Some(new File(swiftfolder, "process-change.swift").toString());

      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, file, kind = Some(swiftkind))
      }

      withActivation(wsk.activation, wsk.action.invoke(name)) {
        activation =>
          activation.response.success shouldBe false
          activation.response.result.get.toString should include("Please make sure name and color are passed in as params.")
      }
    }
}
