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
import common.rest.WskRest
import common.rest.RestResult
import common.ActivationResult
import com.jayway.restassured.RestAssured
import com.jayway.restassured.config.SSLConfig
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps


@RunWith(classOf[JUnitRunner])
class CloudantTests extends TestHelpers
  with WskTestHelpers
  with BeforeAndAfterAll {

  implicit val wskprops = WskProps()
  val wsk = new Wsk()
  val wskRest: common.rest.WskRest = new WskRest
  val allowedActionDuration = 120 seconds

  // statuses for deployWeb
  val successStatus =
    """"status":"success""""

  val deployTestRepo = "https://github.com/ibm-functions/template-cloudant-trigger"
  val cloudantAction = "myPackage/process-change"
  val cloudantSequence = "myPackage/process-change-cloudant-sequence"
  val packageName = "myPackage"
  val ruleName = "myRule"
  val triggerName = "myTrigger"
  val binding = "openwhisk-cloudant"
  val fakeChangesAction = "openwhisk-cloudant/changes"
  val deployAction = "/whisk.system/deployWeb/wskdeploy"
  val deployActionURL = s"https://${wskprops.apihost}/api/v1/web${deployAction}.http"

  //set parameters for deploy tests
  val node8RuntimePath = "runtimes/nodejs"
  val nodejs8folder = "../runtimes/nodejs/actions";
  val nodejs8kind = "nodejs:8"
  val node6RuntimePath = "runtimes/nodejs-6"
  val nodejs6folder = "../runtimes/nodejs-6/actions";
  val nodejs6kind = "nodejs:6"
  val phpRuntimePath = "runtimes/php"
  val phpfolder = "../runtimes/php/actions";
  val phpkind = "php:7.1"
  val pythonRuntimePath = "runtimes/python"
  val pythonfolder = "../runtimes/python/actions";
  val pythonkind = "python-jessie:3"
  val swiftRuntimePath = "runtimes/swift"
  val swiftfolder = "../runtimes/swift/actions";
  val swiftkind = "swift:3.1.1"

  behavior of "Cloudant Trigger Template"

  // test to create the nodejs 8 cloudant trigger template from github url.  Will use preinstalled folder.
  it should "create the nodejs 8 cloudant trigger action from github url" in {
    makePostCallWithExpectedResult(JsObject(
      "gitUrl" -> JsString(deployTestRepo),
      "manifestPath" -> JsString(node8RuntimePath),
      "envData" -> JsObject(
        "PACKAGE_NAME" -> JsString(packageName),
        "CLOUDANT_USERNAME" -> JsString("username"),
        "CLOUDANT_PASSWORD" -> JsString("password"),
        "CLOUDANT_DATABASE" -> JsString("database"),
        "CLOUDANT_HOSTNAME" -> JsString("hostname"),
        "TRIGGER_NAME" -> JsString(triggerName),
        "RULE_NAME" -> JsString(ruleName)
      ),
      "wskApiHost" -> JsString(wskprops.apihost),
      "wskAuth" -> JsString(wskprops.authKey)
    ), successStatus, 200);

    // check that both actions were created and can be invoked with expected results
    withActivation(wsk.activation, wsk.action.invoke(fakeChangesAction, Map("message" -> "echo".toJson))) {
      _.response.result.get.toString should include("echo")
    }

    withActivation(wsk.activation, wsk.action.invoke(cloudantAction)) {
      _.response.result.get.toString should include("Please make sure name and color are passed in as params.")
    }

    // confirm trigger exists
    val triggers = wsk.trigger.list()
    verifyTriggerList(triggers, triggerName);
    val triggerRun = wsk.trigger.fire(triggerName, Map("name" -> "Kat".toJson, "color" -> "Red".toJson))

    // confirm trigger will fire sequence with expected result
    withActivation(wsk.activation, triggerRun) { activation =>
      val logEntry = activation.logs.get(0).parseJson.asJsObject
      val triggerActivationId: String = logEntry.getFields("activationId")(0).convertTo[String]
      withActivation(wsk.activation, triggerActivationId) { triggerActivation =>
        triggerActivation.response.result.get.toString should include ("A Red cat named Kat was added");
      }
    }

    // confirm rule exists
    val rules = wsk.rule.list()
    verifyRule(rules, ruleName, triggerName, cloudantSequence)

    // check that sequence was created and is invoked with expected results
    val runSequence = wsk.action.invoke(cloudantSequence, Map("name" -> "Kat".toJson, "color" -> "Red".toJson))
    withActivation(wsk.activation, runSequence, totalWait = 2 * allowedActionDuration) { activation =>
      checkSequenceLogs(activation, 2)
      activation.response.result.get.toString should include("A Red cat named Kat was added")
    }

    val action = wsk.action.get(cloudantAction)
    verifyAction(action, cloudantAction, JsString(nodejs8kind))

    // clean up after test
    wsk.action.delete(cloudantAction)
    wsk.action.delete(cloudantSequence)
    wsk.pkg.delete(binding)
    wsk.pkg.delete(packageName)
    wsk.trigger.delete(triggerName)
    wsk.rule.delete(ruleName)
  }

  // test to create the nodejs 6 cloudant trigger template from github url.  Will use preinstalled folder.
  it should "create the nodejs 6 cloudant trigger action from github url" in {
    makePostCallWithExpectedResult(JsObject(
      "gitUrl" -> JsString(deployTestRepo),
      "manifestPath" -> JsString(node6RuntimePath),
      "envData" -> JsObject(
        "PACKAGE_NAME" -> JsString(packageName),
        "CLOUDANT_USERNAME" -> JsString("username"),
        "CLOUDANT_PASSWORD" -> JsString("password"),
        "CLOUDANT_DATABASE" -> JsString("database"),
        "CLOUDANT_HOSTNAME" -> JsString("hostname"),
        "TRIGGER_NAME" -> JsString(triggerName),
        "RULE_NAME" -> JsString(ruleName)
      ),
      "wskApiHost" -> JsString(wskprops.apihost),
      "wskAuth" -> JsString(wskprops.authKey)
    ), successStatus, 200);

    // check that both actions were created and can be invoked with expected results
    withActivation(wsk.activation, wsk.action.invoke(fakeChangesAction, Map("message" -> "echo".toJson))) {
      _.response.result.get.toString should include("echo")
    }

    withActivation(wsk.activation, wsk.action.invoke(cloudantAction)) {
      _.response.result.get.toString should include("Please make sure name and color are passed in as params.")
    }

    // confirm trigger exists
    val triggers = wsk.trigger.list()
    verifyTriggerList(triggers, triggerName);
    val triggerRun = wsk.trigger.fire(triggerName, Map("name" -> "Kat".toJson, "color" -> "Red".toJson))

    // confirm trigger will fire sequence with expected result
    withActivation(wsk.activation, triggerRun) { activation =>
      val logEntry = activation.logs.get(0).parseJson.asJsObject
      val triggerActivationId: String = logEntry.getFields("activationId")(0).convertTo[String]
      withActivation(wsk.activation, triggerActivationId) { triggerActivation =>
        triggerActivation.response.result.get.toString should include ("A Red cat named Kat was added");
      }
    }

    // confirm rule exists
    val rules = wsk.rule.list()
    verifyRule(rules, ruleName, triggerName, cloudantSequence)

    // check that sequence was created and is invoked with expected results
    val runSequence = wsk.action.invoke(cloudantSequence, Map("name" -> "Kat".toJson, "color" -> "Red".toJson))
    withActivation(wsk.activation, runSequence, totalWait = 2 * allowedActionDuration) { activation =>
      checkSequenceLogs(activation, 2)
      activation.response.result.get.toString should include("A Red cat named Kat was added")
    }

    val action = wsk.action.get(cloudantAction)
    verifyAction(action, cloudantAction, JsString(nodejs6kind))

    // clean up after test
    wsk.action.delete(cloudantAction)
    wsk.action.delete(cloudantSequence)
    wsk.pkg.delete(packageName)
    wsk.pkg.delete(binding)
    wsk.trigger.delete(triggerName)
    wsk.rule.delete(ruleName)
  }

  // test to create the php cloudant trigger template from github url.  Will use preinstalled folder.
  it should "create the php cloudant trigger action from github url" in {
    makePostCallWithExpectedResult(JsObject(
      "gitUrl" -> JsString(deployTestRepo),
      "manifestPath" -> JsString(phpRuntimePath),
      "envData" -> JsObject(
        "PACKAGE_NAME" -> JsString(packageName),
        "CLOUDANT_USERNAME" -> JsString("username"),
        "CLOUDANT_PASSWORD" -> JsString("password"),
        "CLOUDANT_DATABASE" -> JsString("database"),
        "CLOUDANT_HOSTNAME" -> JsString("hostname"),
        "TRIGGER_NAME" -> JsString(triggerName),
        "RULE_NAME" -> JsString(ruleName)
      ),
      "wskApiHost" -> JsString(wskprops.apihost),
      "wskAuth" -> JsString(wskprops.authKey)
    ), successStatus, 200);

    // check that both actions were created and can be invoked with expected results
    withActivation(wsk.activation, wsk.action.invoke(fakeChangesAction, Map("message" -> "echo".toJson))) {
      _.response.result.get.toString should include("echo")
    }

    withActivation(wsk.activation, wsk.action.invoke(cloudantAction)) {
      _.response.result.get.toString should include("Please make sure name and color are passed in as params.")
    }

    // confirm trigger exists
    val triggers = wsk.trigger.list()
    verifyTriggerList(triggers, triggerName);
    val triggerRun = wsk.trigger.fire(triggerName, Map("name" -> "Kat".toJson, "color" -> "Red".toJson))

    // confirm trigger will fire sequence with expected result
    withActivation(wsk.activation, triggerRun) { activation =>
      val logEntry = activation.logs.get(0).parseJson.asJsObject
      val triggerActivationId: String = logEntry.getFields("activationId")(0).convertTo[String]
      withActivation(wsk.activation, triggerActivationId) { triggerActivation =>
        triggerActivation.response.result.get.toString should include ("A Red cat named Kat was added");
      }
    }

    // confirm rule exists
    val rules = wsk.rule.list()
    verifyRule(rules, ruleName, triggerName, cloudantSequence)

    // check that sequence was created and is invoked with expected results
    val runSequence = wsk.action.invoke(cloudantSequence, Map("name" -> "Kat".toJson, "color" -> "Red".toJson))
    withActivation(wsk.activation, runSequence, totalWait = 2 * allowedActionDuration) { activation =>
      checkSequenceLogs(activation, 2)
      activation.response.result.get.toString should include("A Red cat named Kat was added")
    }

    val action = wsk.action.get(cloudantAction)
    verifyAction(action, cloudantAction, JsString(phpkind))

    // clean up after test
    wsk.action.delete(cloudantAction)
    wsk.action.delete(cloudantSequence)
    wsk.pkg.delete(packageName)
    wsk.pkg.delete(binding)
    wsk.trigger.delete(triggerName)
    wsk.rule.delete(ruleName)
  }

  // test to create the python cloudant trigger template from github url.  Will use preinstalled folder.
  it should "create the python cloudant trigger action from github url" in {
    makePostCallWithExpectedResult(JsObject(
      "gitUrl" -> JsString(deployTestRepo),
      "manifestPath" -> JsString(pythonRuntimePath),
      "envData" -> JsObject(
        "PACKAGE_NAME" -> JsString(packageName),
        "CLOUDANT_USERNAME" -> JsString("username"),
        "CLOUDANT_PASSWORD" -> JsString("password"),
        "CLOUDANT_DATABASE" -> JsString("database"),
        "CLOUDANT_HOSTNAME" -> JsString("hostname"),
        "TRIGGER_NAME" -> JsString(triggerName),
        "RULE_NAME" -> JsString(ruleName)
      ),
      "wskApiHost" -> JsString(wskprops.apihost),
      "wskAuth" -> JsString(wskprops.authKey)
    ), successStatus, 200);
    // check that both actions were created and can be invoked with expected results
    withActivation(wsk.activation, wsk.action.invoke(fakeChangesAction, Map("message" -> "echo".toJson))) {
      _.response.result.get.toString should include("echo")
    }

    withActivation(wsk.activation, wsk.action.invoke(cloudantAction)) {
      _.response.result.get.toString should include("Please make sure name and color are passed in as params.")
    }

    // confirm trigger exists
    val triggers = wsk.trigger.list()
    verifyTriggerList(triggers, triggerName);
    val triggerRun = wsk.trigger.fire(triggerName, Map("name" -> "Kat".toJson, "color" -> "Red".toJson))

    // confirm trigger will fire sequence with expected result
    withActivation(wsk.activation, triggerRun) { activation =>
      val logEntry = activation.logs.get(0).parseJson.asJsObject
      val triggerActivationId: String = logEntry.getFields("activationId")(0).convertTo[String]
      withActivation(wsk.activation, triggerActivationId) { triggerActivation =>
        triggerActivation.response.result.get.toString should include ("A Red cat named Kat was added");
      }
    }

    // confirm rule exists
    val rules = wsk.rule.list()
    verifyRule(rules, ruleName, triggerName, cloudantSequence)

    // check that sequence was created and is invoked with expected results
    val runSequence = wsk.action.invoke(cloudantSequence, Map("name" -> "Kat".toJson, "color" -> "Red".toJson))
    withActivation(wsk.activation, runSequence, totalWait = 2 * allowedActionDuration) { activation =>
      checkSequenceLogs(activation, 2)
      activation.response.result.get.toString should include("A Red cat named Kat was added")
    }

    val action = wsk.action.get(cloudantAction)
    verifyAction(action, cloudantAction, JsString(pythonkind))

    // clean up after test
    wsk.action.delete(cloudantAction)
    wsk.action.delete(cloudantSequence)
    wsk.pkg.delete(packageName)
    wsk.pkg.delete(binding)
    wsk.trigger.delete(triggerName)
    wsk.rule.delete(ruleName)
  }

  // test to create the swift cloudant trigger template from github url.  Will use preinstalled folder.
  it should "create the swift cloudant trigger action from github url" in {
    makePostCallWithExpectedResult(JsObject(
      "gitUrl" -> JsString(deployTestRepo),
      "manifestPath" -> JsString(swiftRuntimePath),
      "envData" -> JsObject(
        "PACKAGE_NAME" -> JsString(packageName),
        "CLOUDANT_USERNAME" -> JsString("username"),
        "CLOUDANT_PASSWORD" -> JsString("password"),
        "CLOUDANT_DATABASE" -> JsString("database"),
        "CLOUDANT_HOSTNAME" -> JsString("hostname"),
        "TRIGGER_NAME" -> JsString(triggerName),
        "RULE_NAME" -> JsString(ruleName)
      ),
      "wskApiHost" -> JsString(wskprops.apihost),
      "wskAuth" -> JsString(wskprops.authKey)
    ), successStatus, 200);

    // check that both actions were created and can be invoked with expected results
    withActivation(wsk.activation, wsk.action.invoke(fakeChangesAction, Map("message" -> "echo".toJson))) {
      _.response.result.get.toString should include("echo")
    }

    withActivation(wsk.activation, wsk.action.invoke(cloudantAction)) {
      _.response.result.get.toString should include("Please make sure name and color are passed in as params.")
    }

    // confirm trigger exists
    val triggers = wsk.trigger.list()
    verifyTriggerList(triggers, triggerName);
    val triggerRun = wsk.trigger.fire(triggerName, Map("name" -> "Kat".toJson, "color" -> "Red".toJson))

    // confirm trigger will fire sequence with expected result
    withActivation(wsk.activation, triggerRun) { activation =>
      val logEntry = activation.logs.get(0).parseJson.asJsObject
      val triggerActivationId: String = logEntry.getFields("activationId")(0).convertTo[String]
      withActivation(wsk.activation, triggerActivationId) { triggerActivation =>
        triggerActivation.response.result.get.toString should include ("A Red cat named Kat was added");
      }
    }

    // confirm rule exists
    val rules = wsk.rule.list()
    verifyRule(rules, ruleName, triggerName, cloudantSequence)

    // check that sequence was created and is invoked with expected results
    val runSequence = wsk.action.invoke(cloudantSequence, Map("name" -> "Kat".toJson, "color" -> "Red".toJson))
    withActivation(wsk.activation, runSequence, totalWait = 2 * allowedActionDuration) { activation =>
      checkSequenceLogs(activation, 2)
      activation.response.result.get.toString should include("A Red cat named Kat was added")
    }

    val action = wsk.action.get(cloudantAction)
    verifyAction(action, cloudantAction, JsString(swiftkind))

    // clean up after test
    wsk.action.delete(cloudantAction)
    wsk.action.delete(cloudantSequence)
    wsk.pkg.delete(packageName)
    wsk.pkg.delete(binding)
    wsk.trigger.delete(triggerName)
    wsk.rule.delete(ruleName)
  }

  /**
    * Test the nodejs 8 "cloudant trigger" template
    */
  it should "invoke nodejs 8 process-change.js and get the result" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
    println(System.getProperty("user.dir"));

    val name = "cloudantNode"
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

    val name = "cloudantNode"
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
    println(System.getProperty("user.dir"));

    val name = "cloudantNode"
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

    val name = "cloudantNode"
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

    val name = "cloudantPython"
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

    val name = "cloudantPython"
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

    val name = "cloudantPhp"
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

    val name = "cloudantPhp"
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

    val name = "cloudantSwift"
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

    val name = "cloudantSwift"
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


  private def checkSequenceLogs(activation: ActivationResult, size: Int) = {
    activation.logs shouldBe defined
    // check that the logs are what they are supposed to be (activation ids)
    activation.logs.get.size shouldBe (size) // the number of activations in this sequence
  }

  private def makePostCallWithExpectedResult(params: JsObject, expectedResult: String, expectedCode: Int) = {
    val response = RestAssured.given()
      .contentType("application/json\r\n")
      .config(RestAssured.config().sslConfig(new SSLConfig().relaxedHTTPSValidation()))
      .body(params.toString())
      .post(deployActionURL)
    assert(response.statusCode() == expectedCode)
    response.body.asString should include(expectedResult)
    response.body.asString.parseJson.asJsObject.getFields("activationId") should have length 1
  }

  private def verifyRule(ruleListResult: RunResult, ruleName: String, triggerName: String, actionName: String) = {
    val actionNameWithNoPackage = actionName.split("/").last
    val rule = wskRest.rule.get(ruleName)
    rule.getField("name") shouldBe ruleName
    RestResult.getField(rule.getFieldJsObject("trigger"), "name") shouldBe triggerName
    RestResult.getField(rule.getFieldJsObject("action"), "name") shouldBe actionNameWithNoPackage
  }

  private def verifyTriggerList(triggerListResult: RunResult, triggerName: String) = {
    val triggerList = triggerListResult.stdout
    val listOutput = triggerList.lines
    listOutput.find(_.contains(triggerName)).get should include(triggerName)
  }

  private def verifyAction(action: RunResult, name: String, kindValue: JsString): Unit = {
    val stdout = action.stdout
    assert(stdout.startsWith(s"ok: got action $name\n"))
    wsk.parseJsonString(stdout).fields("exec").asJsObject.fields("kind") shouldBe kindValue
  }
}
