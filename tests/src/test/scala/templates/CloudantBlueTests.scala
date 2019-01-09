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
import common.{TestHelpers, TestUtils, Wsk, WskProps, WskTestHelpers}
import java.io._

import common.TestUtils.RunResult
import com.jayway.restassured.RestAssured
import com.jayway.restassured.config.SSLConfig
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class CloudantBlueTests extends TestHelpers with WskTestHelpers with BeforeAndAfterAll {

  implicit val wskprops = WskProps()
  val wsk = new Wsk()
  val allowedActionDuration = 120 seconds

  // statuses for deployWeb
  val successStatus =
    """"status": "success""""

  val deployTestRepo =
    "https://github.com/ibm-functions/template-cloudant-trigger"
  val cloudantAction = "process-change"
  val cloudantSequence = "process-change-cloudant-sequence"
  val packageName = "myPackage"
  val ruleName = "myRule"
  val triggerName = "myTrigger"
  val binding = "openwhisk-cloudant"
  val cloudantReadAction = binding + "/" + "read"
  val deployAction = "/whisk.system/deployWeb/wskdeploy"
  val deployActionURL =
    s"https://${wskprops.apihost}/api/v1/web${deployAction}.http"
  val namespace = wsk.namespace.whois()

  //set parameters for deploy tests
  val nodejsRuntimePath = "runtimes/nodejs"
  val nodejsfolder = "runtimes/nodejs/actions"
  val nodejskind = "nodejs:10"
  val phpRuntimePath = "runtimes/php"
  val phpfolder = "runtimes/php/actions"
  val phpkind = "php:7.3"
  val pythonRuntimePath = "runtimes/python"
  val pythonfolder = "runtimes/python/actions"
  val pythonkind = "python:3.7"
  val swiftRuntimePath = "runtimes/swift"
  val swiftfolder = "runtimes/swift/actions"
  val swiftkind = "swift:4.2"

  // connect to cloudant db for credentials and to create/destroy DBs
  val creds = TestUtils.getVCAPcredentials("cloudantNoSQLDB")
  val datdir = "tests/dat/"
  val dbNameBase = "template-cloudant-trigger"

  behavior of "Cloudant Trigger Template"

  // test to create the nodejs 10 cloudant trigger template from github url.  Will use preinstalled folder.
  it should "create the nodejs 10 cloudant trigger template from github url" in withAssetCleaner(wskprops) {
    (wp, assetHelper) =>
      // create unique asset names
      val timestamp: String = System.currentTimeMillis.toString
      val nodejs8Package = packageName + timestamp
      val nodejs8Trigger = triggerName + timestamp
      val nodejs8Rule = ruleName + timestamp
      val nodejs8CloudantAction = nodejs8Package + "/" + cloudantAction
      val nodejs8CloudantSequence = nodejs8Package + "/" + cloudantSequence

      // action created from file to create or destroy cloudant db
      val file = Some(new File(datdir, "cloudantUtils.js").toString())
      val dbName = dbNameBase + timestamp
      assetHelper.withCleaner(wsk.action, "cloudantUtils") { (action, _) =>
        action.create("cloudantUtils", file, kind = Some(nodejskind))
      }
      var params = Map(
        "username" -> JsString(creds.get("username")),
        "password" -> JsString(creds.get("password")),
        "dbName" -> JsString(dbName),
        "create" -> JsString("create"))

      withActivation(wsk.activation, wsk.action.invoke("cloudantUtils", params)) {
        _.response.result.get.toString should include(""""ok":true""")
      }

      // post call to deploy package to test deploy of manifest
      makePostCallWithExpectedResult(
        JsObject(
          "gitUrl" -> JsString(deployTestRepo),
          "manifestPath" -> JsString(nodejsRuntimePath),
          "envData" -> JsObject(
            "PACKAGE_NAME" -> JsString(nodejs8Package),
            "CLOUDANT_USERNAME" -> JsString(creds.get("username")),
            "CLOUDANT_PASSWORD" -> JsString(creds.get("password")),
            "CLOUDANT_DATABASE" -> JsString(dbName),
            "CLOUDANT_HOSTNAME" -> JsString(creds.get("host")),
            "TRIGGER_NAME" -> JsString(nodejs8Trigger),
            "RULE_NAME" -> JsString(nodejs8Rule)),
          "wskApiHost" -> JsString(wskprops.apihost),
          "wskAuth" -> JsString(wskprops.authKey)),
        successStatus,
        200)

      // check that both actions were created and can be invoked
      withActivation(wsk.activation, wsk.action.invoke(cloudantReadAction)) {
        _.response.result.get.toString should include("dbname is required.")
      }

      withActivation(wsk.activation, wsk.action.invoke(nodejs8CloudantAction)) {
        _.response.result.get.toString should include("Please make sure name and color are passed in as params.")
      }

      // confirm trigger exists
      val triggers = wsk.trigger.list()
      verifyTriggerList(triggers, nodejs8Trigger)

      // confirm trigger will fire
      val triggerRun = wsk.trigger.fire(nodejs8Trigger)
      withActivation(wsk.activation, triggerRun) { activation =>
        val logEntry = activation.logs.get(0).parseJson.asJsObject
        val triggerActivationId: String =
          logEntry.getFields("activationId")(0).convertTo[String]
        withActivation(wsk.activation, triggerActivationId) { triggerActivation =>
          triggerActivation.response.result.get.toString should include("dbname is required")
        }
      }

      // confirm rule exists
      val rules = wsk.rule.list()
      verifyRule(rules, nodejs8Rule, nodejs8Trigger, nodejs8CloudantSequence)

      // check that sequence was created and contains correct actions
      val compValue = JsArray(
        JsString("/" + namespace + "/" + cloudantReadAction),
        JsString("/" + namespace + "/" + nodejs8CloudantAction))
      val sequence = wsk.action.get(nodejs8CloudantSequence)
      verifyActionSequence(sequence, nodejs8CloudantSequence, compValue, JsString("sequence"))

      // verify action exists as correct kind
      val action = wsk.action.get(nodejs8CloudantAction)
      verifyAction(action, nodejs8CloudantAction, JsString(nodejskind))

      // clean up after test
      // destroy db that was created for this test
      params = Map(
        "username" -> JsString(creds.get("username")),
        "password" -> JsString(creds.get("password")),
        "dbName" -> JsString(dbName),
        "create" -> JsString("destroy"))

      withActivation(wsk.activation, wsk.action.invoke("cloudantUtils", params)) {
        _.response.result.get.toString should include(""""ok":true""")
      }

      wsk.action.delete(nodejs8CloudantAction)
      wsk.action.delete(nodejs8CloudantSequence)
      wsk.pkg.delete(binding)
      wsk.pkg.delete(nodejs8Package)
      wsk.trigger.delete(nodejs8Trigger)
      wsk.rule.delete(nodejs8Rule)
  }

  // test to create the php cloudant trigger template from github url.  Will use preinstalled folder.
  it should "create the php cloudant trigger template from github url" in withAssetCleaner(wskprops) {
    (wp, assetHelper) =>
      // create unique asset names
      val timestamp: String = System.currentTimeMillis.toString
      val phpPackage = packageName + timestamp
      val phpTrigger = triggerName + timestamp
      val phpRule = ruleName + timestamp
      val phpCloudantAction = phpPackage + "/" + cloudantAction
      val phpCloudantSequence = phpPackage + "/" + cloudantSequence

      // action created from file to create or destroy cloudant db
      val file = Some(new File(datdir, "cloudantUtils.js").toString())
      val dbName = dbNameBase + timestamp
      assetHelper.withCleaner(wsk.action, "cloudantUtils") { (action, _) =>
        action.create("cloudantUtils", file, kind = Some(nodejskind))
      }
      var params = Map(
        "username" -> JsString(creds.get("username")),
        "password" -> JsString(creds.get("password")),
        "dbName" -> JsString(dbName),
        "create" -> JsString("create"))

      withActivation(wsk.activation, wsk.action.invoke("cloudantUtils", params)) {
        _.response.result.get.toString should include(""""ok":true""")
      }

      // post call to deploy package to test deploy of manifest
      makePostCallWithExpectedResult(
        JsObject(
          "gitUrl" -> JsString(deployTestRepo),
          "manifestPath" -> JsString(phpRuntimePath),
          "envData" -> JsObject(
            "PACKAGE_NAME" -> JsString(phpPackage),
            "CLOUDANT_USERNAME" -> JsString(creds.get("username")),
            "CLOUDANT_PASSWORD" -> JsString(creds.get("password")),
            "CLOUDANT_DATABASE" -> JsString(dbName),
            "CLOUDANT_HOSTNAME" -> JsString(creds.get("host")),
            "TRIGGER_NAME" -> JsString(phpTrigger),
            "RULE_NAME" -> JsString(phpRule)),
          "wskApiHost" -> JsString(wskprops.apihost),
          "wskAuth" -> JsString(wskprops.authKey)),
        successStatus,
        200)

      // check that both actions were created and can be invoked
      withActivation(wsk.activation, wsk.action.invoke(cloudantReadAction)) {
        _.response.result.get.toString should include("dbname is required.")
      }

      withActivation(wsk.activation, wsk.action.invoke(phpCloudantAction)) {
        _.response.result.get.toString should include("Please make sure name and color are passed in as params.")
      }

      // confirm trigger exists
      val triggers = wsk.trigger.list()
      verifyTriggerList(triggers, phpTrigger)

      // confirm trigger will fire
      val triggerRun = wsk.trigger.fire(phpTrigger)
      withActivation(wsk.activation, triggerRun) { activation =>
        val logEntry = activation.logs.get(0).parseJson.asJsObject
        val triggerActivationId: String =
          logEntry.getFields("activationId")(0).convertTo[String]
        withActivation(wsk.activation, triggerActivationId) { triggerActivation =>
          triggerActivation.response.result.get.toString should include("dbname is required")
        }
      }

      // confirm rule exists
      val rules = wsk.rule.list()
      verifyRule(rules, phpRule, phpTrigger, phpCloudantSequence)

      // check that sequence was created and contains correct actions
      val compValue = JsArray(
        JsString("/" + namespace + "/" + cloudantReadAction),
        JsString("/" + namespace + "/" + phpCloudantAction))
      val sequence = wsk.action.get(phpCloudantSequence)
      verifyActionSequence(sequence, phpCloudantSequence, compValue, JsString("sequence"))

      // verify action exists as correct kind
      val action = wsk.action.get(phpCloudantAction)
      verifyAction(action, phpCloudantAction, JsString(phpkind))

      // clean up after test
      // destroy db that was created for this test
      params = Map(
        "username" -> JsString(creds.get("username")),
        "password" -> JsString(creds.get("password")),
        "dbName" -> JsString(dbName),
        "create" -> JsString("destroy"))

      withActivation(wsk.activation, wsk.action.invoke("cloudantUtils", params)) {
        _.response.result.get.toString should include(""""ok":true""")
      }

      wsk.action.delete(phpCloudantAction)
      wsk.action.delete(phpCloudantSequence)
      wsk.pkg.delete(binding)
      wsk.pkg.delete(phpPackage)
      wsk.trigger.delete(phpTrigger)
      wsk.rule.delete(phpRule)
  }

  // test to create the python cloudant trigger template from github url.  Will use preinstalled folder.
  it should "create the python cloudant trigger template from github url" in withAssetCleaner(wskprops) {
    (wp, assetHelper) =>
      // create unique asset names
      val timestamp: String = System.currentTimeMillis.toString
      val pythonPackage = packageName + timestamp
      val pythonTrigger = triggerName + timestamp
      val pythonRule = ruleName + timestamp
      val pythonCloudantAction = pythonPackage + "/" + cloudantAction
      val pythonCloudantSequence = pythonPackage + "/" + cloudantSequence

      // action created from file to create or destroy cloudant db
      val file = Some(new File(datdir, "cloudantUtils.js").toString())
      val dbName = dbNameBase + timestamp
      assetHelper.withCleaner(wsk.action, "cloudantUtils") { (action, _) =>
        action.create("cloudantUtils", file, kind = Some(nodejskind))
      }
      var params = Map(
        "username" -> JsString(creds.get("username")),
        "password" -> JsString(creds.get("password")),
        "dbName" -> JsString(dbName),
        "create" -> JsString("create"))

      withActivation(wsk.activation, wsk.action.invoke("cloudantUtils", params)) {
        _.response.result.get.toString should include(""""ok":true""")
      }

      // post call to deploy package to test deploy of manifest
      makePostCallWithExpectedResult(
        JsObject(
          "gitUrl" -> JsString(deployTestRepo),
          "manifestPath" -> JsString(pythonRuntimePath),
          "envData" -> JsObject(
            "PACKAGE_NAME" -> JsString(pythonPackage),
            "CLOUDANT_USERNAME" -> JsString(creds.get("username")),
            "CLOUDANT_PASSWORD" -> JsString(creds.get("password")),
            "CLOUDANT_DATABASE" -> JsString(dbName),
            "CLOUDANT_HOSTNAME" -> JsString(creds.get("host")),
            "TRIGGER_NAME" -> JsString(pythonTrigger),
            "RULE_NAME" -> JsString(pythonRule)),
          "wskApiHost" -> JsString(wskprops.apihost),
          "wskAuth" -> JsString(wskprops.authKey)),
        successStatus,
        200)

      // check that both actions were created and can be invoked
      withActivation(wsk.activation, wsk.action.invoke(cloudantReadAction)) {
        _.response.result.get.toString should include("dbname is required.")
      }

      withActivation(wsk.activation, wsk.action.invoke(pythonCloudantAction)) {
        _.response.result.get.toString should include("Please make sure name and color are passed in as params.")
      }

      // confirm trigger exists
      val triggers = wsk.trigger.list()
      verifyTriggerList(triggers, pythonTrigger)

      // confirm trigger will fire
      val triggerRun = wsk.trigger.fire(pythonTrigger)
      withActivation(wsk.activation, triggerRun) { activation =>
        val logEntry = activation.logs.get(0).parseJson.asJsObject
        val triggerActivationId: String =
          logEntry.getFields("activationId")(0).convertTo[String]
        withActivation(wsk.activation, triggerActivationId) { triggerActivation =>
          triggerActivation.response.result.get.toString should include("dbname is required")
        }
      }

      // confirm rule exists
      val rules = wsk.rule.list()
      verifyRule(rules, pythonRule, pythonTrigger, pythonCloudantSequence)

      // check that sequence was created and contains correct actions
      val compValue = JsArray(
        JsString("/" + namespace + "/" + cloudantReadAction),
        JsString("/" + namespace + "/" + pythonCloudantAction))
      val sequence = wsk.action.get(pythonCloudantSequence)
      verifyActionSequence(sequence, pythonCloudantSequence, compValue, JsString("sequence"))

      // verify action exists as correct kind
      val action = wsk.action.get(pythonCloudantAction)
      verifyAction(action, pythonCloudantAction, JsString(pythonkind))

      // clean up after test
      // destroy db that was created for this test
      params = Map(
        "username" -> JsString(creds.get("username")),
        "password" -> JsString(creds.get("password")),
        "dbName" -> JsString(dbName),
        "create" -> JsString("destroy"))

      withActivation(wsk.activation, wsk.action.invoke("cloudantUtils", params)) {
        _.response.result.get.toString should include(""""ok":true""")
      }

      wsk.action.delete(pythonCloudantAction)
      wsk.action.delete(pythonCloudantSequence)
      wsk.pkg.delete(binding)
      wsk.pkg.delete(pythonPackage)
      wsk.trigger.delete(pythonTrigger)
      wsk.rule.delete(pythonRule)
  }

  // test to create the swift cloudant trigger template from github url.  Will use preinstalled folder.
  it should "create the swift cloudant trigger template from github url" in withAssetCleaner(wskprops) {
    (wp, assetHelper) =>
      // create unique asset names
      val timestamp: String = System.currentTimeMillis.toString
      val swiftPackage = packageName + timestamp
      val swiftTrigger = triggerName + timestamp
      val swiftRule = ruleName + timestamp
      val swiftCloudantAction = swiftPackage + "/" + cloudantAction
      val swiftCloudantSequence = swiftPackage + "/" + cloudantSequence

      // action created from file to create or destroy cloudant db
      val file = Some(new File(datdir, "cloudantUtils.js").toString())
      val dbName = dbNameBase + timestamp
      assetHelper.withCleaner(wsk.action, "cloudantUtils") { (action, _) =>
        action.create("cloudantUtils", file, kind = Some(nodejskind))
      }
      var params = Map(
        "username" -> JsString(creds.get("username")),
        "password" -> JsString(creds.get("password")),
        "dbName" -> JsString(dbName),
        "create" -> JsString("create"))

      withActivation(wsk.activation, wsk.action.invoke("cloudantUtils", params)) {
        _.response.result.get.toString should include(""""ok":true""")
      }

      // post call to deploy package to test deploy of manifest
      makePostCallWithExpectedResult(
        JsObject(
          "gitUrl" -> JsString(deployTestRepo),
          "manifestPath" -> JsString(swiftRuntimePath),
          "envData" -> JsObject(
            "PACKAGE_NAME" -> JsString(swiftPackage),
            "CLOUDANT_USERNAME" -> JsString(creds.get("username")),
            "CLOUDANT_PASSWORD" -> JsString(creds.get("password")),
            "CLOUDANT_DATABASE" -> JsString(dbName),
            "CLOUDANT_HOSTNAME" -> JsString(creds.get("host")),
            "TRIGGER_NAME" -> JsString(swiftTrigger),
            "RULE_NAME" -> JsString(swiftRule)),
          "wskApiHost" -> JsString(wskprops.apihost),
          "wskAuth" -> JsString(wskprops.authKey)),
        successStatus,
        200)

      // check that both actions were created and can be invoked
      withActivation(wsk.activation, wsk.action.invoke(cloudantReadAction)) {
        _.response.result.get.toString should include("dbname is required.")
      }

      withActivation(wsk.activation, wsk.action.invoke(swiftCloudantAction)) {
        _.response.result.get.toString should include("Please make sure name and color are passed in as params.")
      }

      // confirm trigger exists
      val triggers = wsk.trigger.list()
      verifyTriggerList(triggers, swiftTrigger)

      // confirm trigger will fire
      val triggerRun = wsk.trigger.fire(swiftTrigger)
      withActivation(wsk.activation, triggerRun) { activation =>
        val logEntry = activation.logs.get(0).parseJson.asJsObject
        val triggerActivationId: String =
          logEntry.getFields("activationId")(0).convertTo[String]
        withActivation(wsk.activation, triggerActivationId) { triggerActivation =>
          triggerActivation.response.result.get.toString should include("dbname is required")
        }
      }

      // confirm rule exists
      val rules = wsk.rule.list()
      verifyRule(rules, swiftRule, swiftTrigger, swiftCloudantSequence)

      // check that sequence was created and contains correct actions
      val compValue = JsArray(
        JsString("/" + namespace + "/" + cloudantReadAction),
        JsString("/" + namespace + "/" + swiftCloudantAction))
      val sequence = wsk.action.get(swiftCloudantSequence)
      verifyActionSequence(sequence, swiftCloudantSequence, compValue, JsString("sequence"))

      // verify action exists as correct kind
      val action = wsk.action.get(swiftCloudantAction)
      verifyAction(action, swiftCloudantAction, JsString(swiftkind))

      // clean up after test
      // destroy db that was created for this test
      params = Map(
        "username" -> JsString(creds.get("username")),
        "password" -> JsString(creds.get("password")),
        "dbName" -> JsString(dbName),
        "create" -> JsString("destroy"))

      withActivation(wsk.activation, wsk.action.invoke("cloudantUtils", params)) {
        _.response.result.get.toString should include(""""ok":true""")
      }

      wsk.action.delete(swiftCloudantAction)
      wsk.action.delete(swiftCloudantSequence)
      wsk.pkg.delete(binding)
      wsk.pkg.delete(swiftPackage)
      wsk.trigger.delete(swiftTrigger)
      wsk.rule.delete(swiftRule)
  }

  /**
   * Test the nodejs 10 "cloudant trigger" template
   */
  it should "invoke nodejs 10 process-change.js and get the result" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
    val timestamp: String = System.currentTimeMillis.toString
    val name = "cloudantNode" + timestamp
    val file = Some(new File(nodejsfolder, "process-change.js").toString())
    assetHelper.withCleaner(wsk.action, name) { (action, _) =>
      action.create(name, file, kind = Some(nodejskind))
    }

    val params = Map("color" -> "Red", "name" -> "Kat").mapValues(_.toJson)

    withActivation(wsk.activation, wsk.action.invoke(name, params)) {
      _.response.result.get.toString should include("A Red cat named Kat was added")
    }
  }

  it should "invoke nodejs 10 process-change.js without parameters and get an error" in withAssetCleaner(wskprops) {
    (wp, assetHelper) =>
      val timestamp: String = System.currentTimeMillis.toString
      val name = "cloudantNode" + timestamp
      val file = Some(new File(nodejsfolder, "process-change.js").toString())

      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, file, kind = Some(nodejskind))
      }

      withActivation(wsk.activation, wsk.action.invoke(name)) { activation =>
        activation.response.success shouldBe false
        activation.response.result.get.toString should include(
          "Please make sure name and color are passed in as params.")
      }
  }

  /**
   * Test the python "cloudant trigger" template
   */
  it should "invoke process-change.py and get the result" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
    val timestamp: String = System.currentTimeMillis.toString
    val name = "cloudantPython" + timestamp
    val file = Some(new File(pythonfolder, "process-change.py").toString())
    assetHelper.withCleaner(wsk.action, name) { (action, _) =>
      action.create(name, file, kind = Some(pythonkind))
    }

    val params = Map("color" -> "Red", "name" -> "Kat").mapValues(_.toJson)

    withActivation(wsk.activation, wsk.action.invoke(name, params)) {
      _.response.result.get.toString should include("A Red cat named Kat was added")
    }
  }
  it should "invoke process-change.py without parameters and get an error" in withAssetCleaner(wskprops) {
    (wp, assetHelper) =>
      val timestamp: String = System.currentTimeMillis.toString
      val name = "cloudantPython" + timestamp
      val file = Some(new File(pythonfolder, "process-change.py").toString())

      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, file, kind = Some(pythonkind))
      }

      withActivation(wsk.activation, wsk.action.invoke(name)) { activation =>
        activation.response.success shouldBe false
        activation.response.result.get.toString should include(
          "Please make sure name and color are passed in as params.")
      }
  }

  /**
   * Test the php "cloudant trigger" template
   */
  it should "invoke process-change.php and get the result" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
    val timestamp: String = System.currentTimeMillis.toString
    val name = "cloudantPhp" + timestamp
    val file = Some(new File(phpfolder, "process-change.php").toString())
    assetHelper.withCleaner(wsk.action, name) { (action, _) =>
      action.create(name, file, kind = Some(phpkind))
    }

    val params = Map("color" -> "Red", "name" -> "Kat").mapValues(_.toJson)

    withActivation(wsk.activation, wsk.action.invoke(name, params)) {
      _.response.result.get.toString should include("A Red cat named Kat was added")
    }
  }
  it should "invoke process-change.php without parameters and get an error" in withAssetCleaner(wskprops) {
    (wp, assetHelper) =>
      val timestamp: String = System.currentTimeMillis.toString
      val name = "cloudantPhp" + timestamp
      val file = Some(new File(phpfolder, "process-change.php").toString())

      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, file, kind = Some(phpkind))
      }

      withActivation(wsk.activation, wsk.action.invoke(name)) { activation =>
        activation.response.success shouldBe false
        activation.response.result.get.toString should include(
          "Please make sure name and color are passed in as params.")
      }
  }

  /**
   * Test the swift "cloudant trigger" template
   */
  it should "invoke process-change.swift and get the result" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
    val timestamp: String = System.currentTimeMillis.toString
    val name = "cloudantSwift" + timestamp
    val file = Some(new File(swiftfolder, "process-change.swift").toString())
    assetHelper.withCleaner(wsk.action, name) { (action, _) =>
      action.create(name, file, kind = Some(swiftkind))
    }

    val params = Map("color" -> "Red", "name" -> "Kat").mapValues(_.toJson)

    withActivation(wsk.activation, wsk.action.invoke(name, params)) {
      _.response.result.get.toString should include("A Red cat named Kat was added")
    }
  }

  it should "invoke process-change.swift without parameters and get an error" in withAssetCleaner(wskprops) {
    (wp, assetHelper) =>
      val timestamp: String = System.currentTimeMillis.toString
      val name = "cloudantSwift" + timestamp
      val file = Some(new File(swiftfolder, "process-change.swift").toString())

      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, file, kind = Some(swiftkind))
      }

      withActivation(wsk.activation, wsk.action.invoke(name)) { activation =>
        activation.response.success shouldBe false
        activation.response.result.get.toString should include(
          "Please make sure name and color are passed in as params.")
      }
  }

  private def verifyRule(ruleListResult: RunResult, ruleName: String, triggerName: String, actionName: String) = {
    val actionNameWithNoPackage = actionName.split("/").last
    val ruleAsObject = wsk.parseJsonString(wsk.rule.get(ruleName).stdout)
    ruleAsObject.fields("name") shouldBe JsString(ruleName)
    ruleAsObject.fields("trigger").asJsObject.fields("name") shouldBe JsString(triggerName)
    ruleAsObject.fields("action").asJsObject.fields("name") shouldBe JsString(actionNameWithNoPackage)
  }

  private def verifyTriggerList(triggerListResult: RunResult, triggerName: String) = {
    val triggerList = triggerListResult.stdout
    val listOutput = triggerList.lines
    listOutput.find(_.contains(triggerName)).get should include(triggerName)
  }

  private def verifyAction(action: RunResult, name: String, kindValue: JsString): Unit = {
    val stdout = action.stdout
    assert(stdout.startsWith(s"ok: got action $name\n"))
    wsk
      .parseJsonString(stdout)
      .fields("exec")
      .asJsObject
      .fields("kind") shouldBe kindValue
  }

  def verifyActionSequence(action: RunResult, name: String, compValue: JsArray, kindValue: JsString): Unit = {
    val stdout = action.stdout
    assert(stdout.startsWith(s"ok: got action $name\n"))
    wsk
      .parseJsonString(stdout)
      .fields("exec")
      .asJsObject
      .fields("components") shouldBe compValue
    wsk
      .parseJsonString(stdout)
      .fields("exec")
      .asJsObject
      .fields("kind") shouldBe kindValue
  }

  private def makePostCallWithExpectedResult(params: JsObject, expectedResult: String, expectedCode: Int) = {
    val response = RestAssured
      .given()
      .contentType("application/json\r\n")
      .config(
        RestAssured
          .config()
          .sslConfig(new SSLConfig().relaxedHTTPSValidation()))
      .body(params.toString())
      .post(deployActionURL)
    assert(response.statusCode() == expectedCode)
    response.body.asString should include(expectedResult)
    response.body.asString.parseJson.asJsObject
      .getFields("activationId") should have length 1
  }

}
