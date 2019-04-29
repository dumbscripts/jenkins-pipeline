@NonCPS

import hudson.EnvVars

import groovy.json.JsonSlurper

import groovy.transform.Field

 

@Field def slave, checkout, build, test, deploy, artifacts, fortify, blackduck, name, platform

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

    if (name == null || slave == null || checkout == null || build == null || test == null || deploy == null || artifacts == null || fortify == null || blackduck == null || allStages == null) {

        print("Getting stage configuration for stage - ${stage}")

               def res = getPipelineJson(json)

              

        res = new HashMap<>(res)

        print("Got pipeline json  ==> ${res}")

   

        for (item in res) {

               if (!item.value.isEmpty()) {

                   item.value = new HashMap<>(item.value)

                   if (item.value["active"] == true) {

                       print("Setting data for all fields")

                       

                       name = item.key

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

               case "stages" : return allStages

               default: throw Exception("Unknown stage! - ${stage}")

        }

}

 

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

   

        //checkout

    if (allStages.contains('checkout')) {

        def cs = getStageConfig(json, "checkout")

        print ("Found checkout stage - ${cs}")

        checkoutStage(cs);

    }

        //build

    if (allStages.contains("build")) {

        def bs = getStageConfig(json, "build")

        print("Found build stage - ${bs}")

        buildStage("", bs["script"])

    }

    // artifacts

    if (allStages.contains("artifacts")) {

               def art = getStageConfig(json, "artifacts")

        print("Found artifact stage - ${art}")

               artifactStage("", "")

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

        securityStage("", scanMap)

    } else if (allStages.contains("fortify")) {

        def sfs = getStageConfig(json, "fortify")

               print("Found fortify stage - ${sfs}")

        fortifyStage("", sfs["script"])

    } else if (allStages.contains("blackduck")) {

        def sbs = getStageConfig(json, "blackduck")

               print("Found blackduck stage - ${sbs}")

        blackduckStage("", sbs["script"])

    }

    //deploy

    if (allStages.contains("deploy")) {

               def dep = getStageConfig(json, "deploy")

               print("Found deploy stage - ${dep}")

               deployStage("", dep["script"])

        }

    //tests

    if (allStages.contains("test")) {

        def ts = getStageConfig(json, "test")

               print("Found tests stage - ${ts}")

        testStage("", ts["script"])

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

               throw new Exception("Unknown scm - ${scm}")

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

 

def buildStage(n, listScript){

    node (n) {

        stage("Build") {

            executeMultiSteps(listScript)

        }    

    }

}

 

def artifactStage(n, cmd){

    node (n) {

        stage("Artifact Publish") {

            echo "artifact publish"

        }   

    }

}

 

def deployStage(n, listScript) {

    node (n) {

        stage("Deploy") {

                       executeMultiSteps(listScript)

        }   

    }

}

 

def testStage(n, listScript) {

    node (n) {

        stage("Tests") {

            executeMultiSteps(listScript)

        }

    }

}

 

def fortifyStage(n, listScript) {

    node (n) {

        stage("Fortify scan") {

            executeMultiSteps(listScript)

        }   

    }

}

 

def blackduckStage(n, listScript) {

    node (n) {

        stage("Blackduck scan") {

            executeMultiSteps(listScript)

        }   

    }

}

 

def securityStage(n, scanMap) {

    def map = [:]

    map["fortify"] = { fortifyStage(n, scanMap["fortify"]) }

    map["blackduck"] = { blackduckStage(n, scanMap["blackduck"]) }

    map.failFast = true

   

    parallel map

}

 

// Helper methods

 

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

