#!/bin/bash
set -ex

# Build script for Travis-CI.
SCRIPTDIR=$(cd $(dirname "$0") && pwd)
ROOTDIR="$SCRIPTDIR/../.."
HOMEDIR="$ROOTDIR/../"
WHISKDIR="$HOMEDIR/openwhisk"

export OPENWHISK_HOME=${OPENWHISK_HOME:=$WHISKDIR}

PREINSTALL_DIR=${HOMEDIR}/preInstalled

WSK_CLI=${OPENWHISK_HOME}/bin/wsk
WSK_SYSTEM_AUTH_KEY=$(cat ${OPENWHISK_HOME}/ansible/files/auth.whisk.system)
WHISK_APIHOST="172.17.0.1"

# Place this template in correct location to be included in packageDeploy
mkdir -p ${PREINSTALL_DIR}/ibm-functions/template-cloudant-trigger
cp -a ${ROOTDIR}/runtimes ${PREINSTALL_DIR}/ibm-functions/template-cloudant-trigger/

# Install the deploy package
cd $HOMEDIR/incubator-openwhisk-package-deploy/packages
./installCatalog.sh $WSK_SYSTEM_AUTH_KEY $WHISK_APIHOST $WSK_CLI

# Install fake cloudant package
$WSK_CLI package update /whisk.system/cloudant --apihost $WHISK_APIHOST --auth $WSK_SYSTEM_AUTH_KEY --shared yes -i
$WSK_CLI action update /whisk.system/cloudant/changes --copy /whisk.system/utils/echo --apihost $WHISK_APIHOST --auth $WSK_SYSTEM_AUTH_KEY -i
$WSK_CLI action update /whisk.system/cloudant/read --copy /whisk.system/utils/echo --apihost $WHISK_APIHOST --auth $WSK_SYSTEM_AUTH_KEY -i
