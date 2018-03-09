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
import common.TestUtils.RunResult
import com.jayway.restassured.RestAssured
import com.jayway.restassured.config.SSLConfig
import spray.json.DefaultJsonProtocol._
import spray.json._

@RunWith(classOf[JUnitRunner])
class CloudantTests extends TestHelpers
    with WskTestHelpers
    with BeforeAndAfterAll {

    implicit val wskprops = WskProps()
    val wsk = new Wsk()

    // statuses for deployWeb
    val successStatus = """"status":"success""""

    val deployTestRepo = "https://github.com/ibm-functions/template-cloudant-trigger"
    val slackReminderActionPackage = "myPackage/process-change"
    val fakeChangesAction = "openwhisk-cloudant/changes"
    val deployAction = "/whisk.system/deployWeb/wskdeploy"
    val deployActionURL = s"https://${wskprops.apihost}/api/v1/web${deployAction}.http"

    //set parameters for deploy tests
    val node8RuntimePath = "runtimes/nodejs"
    val nodejs8folder = "../runtimes/nodejs/actions";
    val nodejs8kind = JsString("nodejs:8")
    val node6RuntimePath = "runtimes/nodejs-6"
    val nodejs6folder = "../runtimes/nodejs-6/actions";
    val nodejs6kind = JsString("nodejs:6")
    val phpRuntimePath = "runtimes/php"
    val phpfolder = "../runtimes/php/actions";
    val phpkind = JsString("php:7.1")
    val pythonRuntimePath = "runtimes/python"
    val pythonfolder = "../runtimes/python/actions";
    val pythonkind = JsString("python:2")
    val swiftRuntimePath = "runtimes/swift"
    val swiftfolder = "../runtimes/swift/actions";
    val swiftkind = JsString("swift:3.1.1")

    def makePostCallWithExpectedResult(params: JsObject, expectedResult: String, expectedCode: Int) = {
      val response = RestAssured.given()
          .contentType("application/json\r\n")
          .config(RestAssured.config().sslConfig(new SSLConfig().relaxedHTTPSValidation()))
          .body(params.toString())
          .post(deployActionURL)
      assert(response.statusCode() == expectedCode)
      response.body.asString should include(expectedResult)
      response.body.asString.parseJson.asJsObject.getFields("activationId") should have length 1
    }

    def verifyAction(action: RunResult, name: String, kindValue: JsString): Unit = {
      val stdout = action.stdout
      assert(stdout.startsWith(s"ok: got action $name\n"))
      wsk.parseJsonString(stdout).fields("exec").asJsObject.fields("kind") shouldBe kindValue
    }

    behavior of "Cloudant Trigger Template"

    // test to create the nodejs 8 cloudant trigger template from github url.  Will use preinstalled folder.
    it should "create the nodejs 8 cloudant trigger action from github url" in {
      makePostCallWithExpectedResult(JsObject(
        "gitUrl" -> JsString(deployTestRepo),
        "manifestPath" -> JsString(node8RuntimePath),
        "envData" -> JsObject(
            "PACKAGE_NAME" -> JsString("myPackage"),
            "CLOUDANT_USERNAME" -> JsString("username"),
            "CLOUDANT_PASSWORD" -> JsString("password"),
            "CLOUDANT_DATABASE" -> JsString("database"),
            "CLOUDANT_HOSTNAME" -> JsString("hostname"),
            "TRIGGER_NAME" -> JsString("myTrigger"),
            "RULE_NAME" -> JsString("myRule")
        ),
        "wskApiHost" -> JsString(wskprops.apihost),
        "wskAuth" -> JsString(wskprops.authKey)
      ), successStatus, 200);

      withActivation(wsk.activation, wsk.action.invoke(slackReminderActionPackage)) {
        _.response.result.get.toString should include("Please make sure name and color are passed in as params.")
      }

      withActivation(wsk.activation, wsk.action.invoke(fakeChangesAction, Map("message" -> "echo".toJson))) {
        _.response.result.get.toString should include("echo")
      }

      val action = wsk.action.get("myPackage/process-change")
      verifyAction(action, slackReminderActionPackage, nodejs8kind)

      // clean up after test
      wsk.action.delete(slackReminderActionPackage)
    }

    // test to create the nodejs 6 cloudant trigger template from github url.  Will use preinstalled folder.
    it should "create the nodejs 6 cloudant trigger action from github url" in {
      makePostCallWithExpectedResult(JsObject(
        "gitUrl" -> JsString(deployTestRepo),
        "manifestPath" -> JsString(node8RuntimePath),
        "envData" -> JsObject(
            "PACKAGE_NAME" -> JsString("myPackage"),
            "CLOUDANT_USERNAME" -> JsString("username"),
            "CLOUDANT_PASSWORD" -> JsString("password"),
            "CLOUDANT_DATABASE" -> JsString("database"),
            "CLOUDANT_HOSTNAME" -> JsString("hostname"),
            "TRIGGER_NAME" -> JsString("myTrigger"),
            "RULE_NAME" -> JsString("myRule")
        ),
        "wskApiHost" -> JsString(wskprops.apihost),
        "wskAuth" -> JsString(wskprops.authKey)
      ), successStatus, 200);

      withActivation(wsk.activation, wsk.action.invoke(slackReminderActionPackage)) {
        _.response.result.get.toString should include("Please make sure name and color are passed in as params.")
      }

      withActivation(wsk.activation, wsk.action.invoke(fakeChangesAction, Map("message" -> "echo".toJson))) {
        _.response.result.get.toString should include("echo")
      }

      val action = wsk.action.get("myPackage/process-change")
      verifyAction(action, slackReminderActionPackage, nodejs6kind)

      // clean up after test
      wsk.action.delete(slackReminderActionPackage)
    }

    // test to create the php cloudant trigger template from github url.  Will use preinstalled folder.
    it should "create the php cloudant trigger action from github url" in {
      makePostCallWithExpectedResult(JsObject(
        "gitUrl" -> JsString(deployTestRepo),
        "manifestPath" -> JsString(phpRuntimePath),
        "envData" -> JsObject(
            "PACKAGE_NAME" -> JsString("myPackage"),
            "CLOUDANT_USERNAME" -> JsString("username"),
            "CLOUDANT_PASSWORD" -> JsString("password"),
            "CLOUDANT_DATABASE" -> JsString("database"),
            "CLOUDANT_HOSTNAME" -> JsString("hostname"),
            "TRIGGER_NAME" -> JsString("myTrigger"),
            "RULE_NAME" -> JsString("myRule")
        ),
        "wskApiHost" -> JsString(wskprops.apihost),
        "wskAuth" -> JsString(wskprops.authKey)
      ), successStatus, 200);

      withActivation(wsk.activation, wsk.action.invoke(slackReminderActionPackage)) {
        _.response.result.get.toString should include("Please make sure name and color are passed in as params.")
      }

      withActivation(wsk.activation, wsk.action.invoke(fakeChangesAction, Map("message" -> "echo".toJson))) {
        _.response.result.get.toString should include("echo")
      }

      val action = wsk.action.get("myPackage/process-change")
      verifyAction(action, slackReminderActionPackage, phpkind)

      // clean up after test
      wsk.action.delete(slackReminderActionPackage)
    }

    // test to create the python cloudant trigger template from github url.  Will use preinstalled folder.
    it should "create the python cloudant trigger action from github url" in {
      makePostCallWithExpectedResult(JsObject(
        "gitUrl" -> JsString(deployTestRepo),
        "manifestPath" -> JsString(pythonRuntimePath),
        "envData" -> JsObject(
            "PACKAGE_NAME" -> JsString("myPackage"),
            "CLOUDANT_USERNAME" -> JsString("username"),
            "CLOUDANT_PASSWORD" -> JsString("password"),
            "CLOUDANT_DATABASE" -> JsString("database"),
            "CLOUDANT_HOSTNAME" -> JsString("hostname"),
            "TRIGGER_NAME" -> JsString("myTrigger"),
            "RULE_NAME" -> JsString("myRule")
        ),
        "wskApiHost" -> JsString(wskprops.apihost),
        "wskAuth" -> JsString(wskprops.authKey)
      ), successStatus, 200);

      withActivation(wsk.activation, wsk.action.invoke(slackReminderActionPackage)) {
        _.response.result.get.toString should include("Please make sure name and color are passed in as params.")
      }

      withActivation(wsk.activation, wsk.action.invoke(fakeChangesAction, Map("message" -> "echo".toJson))) {
        _.response.result.get.toString should include("echo")
      }

      val action = wsk.action.get("myPackage/process-change")
      verifyAction(action, slackReminderActionPackage, pythonkind)

      // clean up after test
      wsk.action.delete(slackReminderActionPackage)
    }

    // test to create the swift cloudant trigger template from github url.  Will use preinstalled folder.
    it should "create the swift cloudant trigger action from github url" in {
      makePostCallWithExpectedResult(JsObject(
        "gitUrl" -> JsString(deployTestRepo),
        "manifestPath" -> JsString(swiftRuntimePath),
        "envData" -> JsObject(
            "PACKAGE_NAME" -> JsString("myPackage"),
            "CLOUDANT_USERNAME" -> JsString("username"),
            "CLOUDANT_PASSWORD" -> JsString("password"),
            "CLOUDANT_DATABASE" -> JsString("database"),
            "CLOUDANT_HOSTNAME" -> JsString("hostname"),
            "TRIGGER_NAME" -> JsString("myTrigger"),
            "RULE_NAME" -> JsString("myRule")
        ),
        "wskApiHost" -> JsString(wskprops.apihost),
        "wskAuth" -> JsString(wskprops.authKey)
      ), successStatus, 200);

      withActivation(wsk.activation, wsk.action.invoke(slackReminderActionPackage)) {
        _.response.result.get.toString should include("Please make sure name and color are passed in as params.")
      }

      withActivation(wsk.activation, wsk.action.invoke(fakeChangesAction, Map("message" -> "echo".toJson))) {
        _.response.result.get.toString should include("echo")
      }

      val action = wsk.action.get("myPackage/process-change")
      verifyAction(action, slackReminderActionPackage, swiftkind)

      // clean up after test
      wsk.action.delete(slackReminderActionPackage)
    }

    /**
     * Test the nodejs 8 "cloudant trigger" template
     */
     it should "invoke nodejs 8 process-change.js and get the result" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
       println(System.getProperty("user.dir"));

       val name = "cloudantNode"
       val file = Some(new File(nodejs8folder, "process-change.js").toString());
       assetHelper.withCleaner(wsk.action, name) { (action, _) =>
         action.create(name, file)
       }

       val params = Map("color" -> "Red", "name" -> "Kat").mapValues(_.toJson)

       withActivation(wsk.activation, wsk.action.invoke(name, params)) {
         _.response.result.get.toString should include("A Red cat named Kat was added")
       }
     }

    it should "invoke nodejs 8 process-change.js without parameters and get an error" in withAssetCleaner(wskprops) { (wp, assetHelper) =>

      val name = "cloudantNode"
      val file = Some(new File(nodejs8folder, "process-change.js").toString());

      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, file)
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
       println(System.getProperty("user.dir"));

       val name = "cloudantNode"
       val file = Some(new File(nodejs6folder, "process-change.js").toString());
       assetHelper.withCleaner(wsk.action, name) { (action, _) =>
         action.create(name, file)
       }

       val params = Map("color" -> "Red", "name" -> "Kat").mapValues(_.toJson)

       withActivation(wsk.activation, wsk.action.invoke(name, params)) {
         _.response.result.get.toString should include("A Red cat named Kat was added")
       }
     }

    it should "invoke nodejs 6 process-change.js without parameters and get an error" in withAssetCleaner(wskprops) { (wp, assetHelper) =>

      val name = "cloudantNode"
      val file = Some(new File(nodejs6folder, "process-change.js").toString());

      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, file)
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

    val name = "cloudantPython"
    val file = Some(new File(pythonfolder, "process-change.py").toString());
    assetHelper.withCleaner(wsk.action, name) { (action, _) =>
      action.create(name, file)
    }

    val params = Map("color" -> "Red", "name" -> "Kat").mapValues(_.toJson)

    withActivation(wsk.activation, wsk.action.invoke(name, params)) {
      _.response.result.get.toString should include("A Red cat named Kat was added")
    }
  }
  it should "invoke process-change.py without parameters and get an error" in withAssetCleaner(wskprops) { (wp, assetHelper) =>

    val name = "cloudantPython"
    val file = Some(new File(pythonfolder, "process-change.py").toString());

    assetHelper.withCleaner(wsk.action, name) { (action, _) =>
      action.create(name, file)
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

    val name = "cloudantPhp"
    val file = Some(new File(phpfolder, "process-change.php").toString());
    assetHelper.withCleaner(wsk.action, name) { (action, _) =>
      action.create(name, file)
    }

    val params = Map("color" -> "Red", "name" -> "Kat").mapValues(_.toJson)

    withActivation(wsk.activation, wsk.action.invoke(name, params)) {
      _.response.result.get.toString should include("A Red cat named Kat was added")
    }
  }
  it should "invoke process-change.php without parameters and get an error" in withAssetCleaner(wskprops) { (wp, assetHelper) =>

    val name = "cloudantPhp"
    val file = Some(new File(phpfolder, "process-change.php").toString());

    assetHelper.withCleaner(wsk.action, name) { (action, _) =>
      action.create(name, file)
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

    val name = "cloudantSwift"
    val file = Some(new File(swiftfolder, "process-change.swift").toString());
    assetHelper.withCleaner(wsk.action, name) { (action, _) =>
      action.create(name, file)
    }

    val params = Map("color" -> "Red", "name" -> "Kat").mapValues(_.toJson)

    withActivation(wsk.activation, wsk.action.invoke(name, params)) {
      _.response.result.get.toString should include("A Red cat named Kat was added")
    }
  }
  it should "invoke process-change.swift without parameters and get an error" in withAssetCleaner(wskprops) { (wp, assetHelper) =>

    val name = "cloudantSwift"
    val file = Some(new File(swiftfolder, "process-change.swift").toString());

    assetHelper.withCleaner(wsk.action, name) { (action, _) =>
      action.create(name, file)
    }

    withActivation(wsk.activation, wsk.action.invoke(name)) {
      activation =>
        activation.response.success shouldBe false
        activation.response.result.get.toString should include("Please make sure name and color are passed in as params.")
    }
  }
}
