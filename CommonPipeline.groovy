@NonCPS

import hudson.EnvVars

import groovy.json.JsonSlurper

import groovy.transform.Field

 

@Field def slave, checkout, build, test, deploy, artifacts, fortify, blackduck, name, platform, tics, props, environment

@Field ArrayList<String> allStages;

 

def getPipelineJson(json) {

        def jsonSlurper = new JsonSlurper()

        return new HashMap<>(jsonSlurper.parseText(json))

}

 

def convertLazyMap(Map lazyMap) {

    hMap = new HashMap<>();

    for ( prop in lazyMap ) {

        hMap[prop.key] = prop.value

    }

    return hMap

}

 

def getStageConfig(json, stage) {

    if (name == null || slave == null || checkout == null || build == null || test == null || deploy == null || artifacts == null || fortify == null || blackduck == null || allStages == null || tics == null || props == null || environment == null) {

        print("Getting stage configuration for stage - ${stage}")

               def res = getPipelineJson(json)

              

        res = new HashMap<>(res)

        print("Got pipeline json  ==> ${res}")

   

        for (item in res) {

               if (!item.value.isEmpty()) {

                   item.value = new HashMap<>(item.value)

                   name = item.key

                   if (item.value["active"] == true) {

                       print("Setting data for all fields")

                       platform = item.value["platform"]

                       allStages = item.value["stages"].keySet().collect()

                       slave = item.value["agent"]

                       checkout = convertLazyMap(item.value["stages"]["checkout"])

                       build = convertLazyMap(item.value["stages"]["build"])

                       artifacts = convertLazyMap(item.value["stages"]["artifacts"])

                       test = convertLazyMap(item.value["stages"]["test"])

                       deploy = convertLazyMap(item.value["stages"]["deploy"])

                       fortify = convertLazyMap(item.value["stages"]["fortify"])

                       blackduck = convertLazyMap(item.value["stages"]["blackduck"])

                       tics = convertLazyMap(item.value["stages"]["tics"])

                       props = convertLazyMap(item.value["properties"])

                       environment = convertLazyMap(item.value["environment"])

                   } else {

                      print("NO ACTIVE PIPELINE DEFINITION FOUND!")

                              }

               }

       }

    }

        switch (stage.toLowerCase()) {

            case "name" : return name

               case "slave" : return agent

               case "checkout" : return checkout

               case "build" : return build

               case "test" : return test

               case "deploy" : return deploy

               case "artifacts" : return artifacts

               case "fortify" : return fortify

               case "blackduck" : return blackduck

               case "tics" : return tics

               case "stages" : return allStages

               case "properties" : return props

               case "environment" : return environment

               default: error "UNKNOWN STAGE! - ${stage}"

        }

}

 

// Add pipelines and execute

def addStagesAndExecutePipeline(json) {

    print("Executing pipeline")

       

    List allStages = getStageConfig(json, "stages")

    print("All stages - ${allStages}")

   

    def jobName = getStageConfig(json, "name")

    // Add node data

    node {

        currentBuild.displayName = "${jobName}_${BUILD_NUMBER}"

        currentBuild.description = "Pipeline for - ${jobName}"

    }

       

        // set properties for pipeline

        def pMap = getStageConfig(json, "properties")

        print("Found properties for pipeline - ${pMap}")

        addPropertiesForPipeline(pMap)

       

        //get environment details for pipeline

        def envMap = getStageConfig(json, "environment")

        print("Found environment for pipeline - ${envMap}")

       

        //checkout

        if (allStages != null) {

               if (allStages.contains('checkout')) {

                       def cs = getStageConfig(json, "checkout")

                       print ("Found checkout stage - ${cs}")

                       checkoutStage(cs);

               }

               //build

               if (allStages.contains("build")) {

                       def bs = getStageConfig(json, "build")

                       print("Found build stage - ${bs}")

                       executeStage("", bs["script"], envMap, "Build")

               }

               // artifacts

               if (allStages.contains("artifacts")) {

                       def art = getStageConfig(json, "artifacts")

                       print("Found artifact stage - ${art}")

                       artifactStage("", art)

               }

               //tics

               if (allStages.contains("tics")) {

                       def tic = getStageConfig(json, "tics")

                       print("Found tics stage - ${tic}")

                       def qualMap = [:]

                       qualMap["tics"] = tic["script"]

                       executeParallelStage("", qualMap, envMap)

               }

 

               //security

               if (allStages.contains("fortify") && allStages.contains("blackduck")) {

                       def ssParamsFortify = getStageConfig(json, "fortify")

                       def ssParamsBlackduck = getStageConfig(json, "blackduck")

                       def scanMap = [:]

                       scanMap["fortify"] = ssParamsFortify["script"]

                       scanMap["blackduck"] = ssParamsBlackduck["script"]

                       print("Found fortify stage - ${ssParamsFortify}")

                       print("Found blackduck stage - ${ssParamsBlackduck}")

                       executeParallelStage("", scanMap, envMap)

               } else if (allStages.contains("fortify")) {

                       def sfs = getStageConfig(json, "fortify")

                       print("Found fortify stage - ${sfs}")

                       executeStage("", sfs["script"], envMap, "Fortify")

               } else if (allStages.contains("blackduck")) {

                       def sbs = getStageConfig(json, "blackduck")

                       print("Found blackduck stage - ${sbs}")

                       executeStage("", sbs["script"], envMap, "Blackduck")     

               }

               //deploy

               if (allStages.contains("deploy")) {

                       def dep = getStageConfig(json, "deploy")

                       print("Found deploy stage - ${dep}")

                       executeStage("", dep["script"], envMap, "Deploy")

               }

               //tests

               if (allStages.contains("test")) {

                       def ts = getStageConfig(json, "test")

                       print("Found tests stage - ${ts}")

                       executeStage("", ts["script"], envMap, "Tests")                   

               }

        }

   

}

 

// logic for checkout stage

def checkoutStage(mapCheckout){

        def scm = mapCheckout["scm"].toLowerCase()

        def n = slave

        def repo = mapCheckout["repo"]

        def branch = mapCheckout["branch"]

        def credsId = mapCheckout["credsId"]

        if ( scm == "git") {

               checkoutGitRepo('', repo, branch, credsId)

        } else if (scm == "tfvc") {

               checkoutTFVCRepo('', repo, branch, credsId)

        } else {

               error "UNKNOWN SCM - ${scm}"

        }

}

 

// checkout from a git repo

def checkoutGitRepo(n, repo, branch, credsId) {

        node (n) {

        stage('Checkout') {

            checkout(

                [$class: "GitSCM",

                branches: [[name: branch]],

                doGenerateSubmoduleConfigurations: false,

                extension: [],

                submoduleCfg: [],

                userRemoteConfigs: [[credentialsId: credsId, url: repo]]])

        }   

    }

}

 

// checkout from a TFVS repo

def checkoutTFVCRepo(n, repo, branch, credsId) {

        node (n) {

               stage('Checkout') {

                       checkout(

                              [$class: 'TeamFoundationServerScm',

                              serverUrl: repo,                            

                              projectPath: branch,

                              useOverwrite: true,

                              useUpdate: true,

                              credentialsConfigurer: [[$class: 'AutomaticCredentialsConfigurer']]])

               }

        }

}

 

def artifactStage(n, mapArtifact){

    node (n) {

        stage("Artifact Publish") {

            echo "Publishing artifacts ..."

                       //publishArtifact(mapArtifact)

        }   

    }

}

 

 

// Helper methods

 

// common stage method to execute stages within environment

def executeStage(n, listSteps, envMap, stageName) {

        def envList = []

        envMap.each { k, v -> envList.add("${k}=${v}") }

        print ("Using environment for stages/steps ==> ${envList}")

        node (n) {

               // set environment

               withEnv(envList) {

                       stage(stageName) {

                                      executeMultiSteps(listSteps)

                       }   

               }

        }

}

 

// parallel stage execution

def executeParallelStage(n, scanMap, envMap) {

        def map = [:]

        scanMap.each { k, v ->

               map[k.capitalize()] = { executeStage(n, v, envMap, k.capitalize()) }

        }

        map.failFast = true

        parallel map

}

 

 

def executeMultiSteps(listSteps) {

        for (item in listSteps) {

               print("Executing step - ${item}")

               if (platform == "linux") {

                       sh "${item}"

               } else {

                       bat "${item}"

               }

        }

}

 

def publishArtifact(artifactData) {

    if (artifactData != {}) {

               def aServer = Artifactory.newServer url: "${artifactData['server']}", credentialsId: "${artifactData['credsId']}"

       def uploadSpec = """{

               "files": [{

                       "pattern": "${artifactData['filePattern']}",

                       "target": "${artifactData['targetPath']}"

                    }]

               }"""

               aServer.bypassProxy = true                 

               aServer.upload(uploadSpec)

    }

}

 

def addPropertiesForPipeline(propsMap) {

        def sched = propsMap["schedule"]

        if (sched != "") {

               print("Setting properties for pipeline...")

               properties([[$class: 'BuildDiscarderProperty',

                strategy: [$class: 'LogRotator', numToKeepStr: '10']],

                pipelineTriggers([[$class: "TimerTrigger", spec: "${propsMap["schedule"]}"]])

                ])

        }

}