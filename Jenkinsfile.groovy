#!/usr/bin/groovy
import com.evobanco.NodejsUtils
import com.evobanco.Constants

def runNodejsGenericJenkinsfile() {

    def utils = new com.evobanco.NodejsUtils()

    def artifactorySnapshotsURL = 'https://digitalservices.evobanco.com/artifactory/libs-snapshot-local'
    def artifactoryReleasesURL = 'https://digitalservices.evobanco.com/artifactory/libs-release-local'

    def npmRepositoryURL = 'https://digitalservices.evobanco.com/artifactory/npm-release-local'

    def sonarQube = 'http://sonarqube:9000'
    def openshiftURL = 'https://openshift.grupoevo.corp:8443'
    def openshiftCredential = 'openshift'
    def registry = '172.20.253.34'
    def artifactoryCredential = 'artifactory-token'
    def jenkinsNamespace = 'cicd'
    def params
    def envLabel
    def branchName
    def branchNameHY
    def branchType


    //Parallel project configuration (PPC) properties
    def branchPPC = 'master'
    def credentialsIdPPCDefault = '4b18ea85-c50b-40f4-9a81-e89e44e20178' //credentials of the parallel configuration project
    def credentialsIdPPC
    def relativeTargetDirPPC = '/tmp/configs/PPC/'
    def isPPCJenkinsFile = false
    def isPPCJenkinsYaml = false
    def isPPCOpenshiftTemplate = false
    def jenkinsFilePathPPC = relativeTargetDirPPC + 'Jenkinsfile'
    def jenkinsYamlPathPPC = relativeTargetDirPPC + 'Jenkins.yml'
    def openshiftNodejsTemplatePathPPC = relativeTargetDirPPC + 'kube/nodejs_template.yaml'
    def jenknsFilePipelinePPC


    //Generic project configuration properties
    def gitDefaultProjectConfigurationPath='https://github.com/isanmartin0/evo-cicd-nodejs-generic-configuration'
    def relativeTargetDirGenericPGC = '/tmp/configs/generic/'
    def branchGenericPGC = 'master'
    def credentialsIdGenericPGC = '4b18ea85-c50b-40f4-9a81-e89e44e20178' //credentials of the generic configuration project
    def jenkinsYamlGenericPath = relativeTargetDirGenericPGC + 'Jenkins.yml'
    def openshiftNodejsTemplateGenericPath = relativeTargetDirGenericPGC + 'kube/nodejs_template.yaml'
    def isGenericJenkinsYaml = false


    def packageJSON
    def projectURL
    def packageName
    def packageVersion
    def packageTag
    def packageTarball
    def isScopedPackage
    def packageScope


    int maxOldBuildsToKeep = 0
    int daysOldBuildsToKeep = 0

    //Taurus parameters
    def taurus_test_base_path = 'src/test/taurus'
    def acceptance_test_path = '/acceptance_test/'
    def performance_test_path = '/performance_test/'
    def smoke_test_path = '/smoke_test/'
    def security_test_path = '/security_test/'


    def openshift_route_hostname = ''
    def openshift_route_hostname_with_protocol = ''

    //Parameters nodejs
    int port_default = 8080
    int debug_port_default = 5858
    int image_stream_nodejs_version_default = 8

    def build_from_registry_url = 'https://github.com/isanmartin0/s2i-nodejs-container.git'
    def build_from_artifact_branch = 'master'

    echo "BEGIN NODE.JS GENERIC CONFIGURATION PROJECT (PGC)"

    node('nodejs') {

        stage('Checkout') {
            echo 'Getting source code...'
            checkout scm
            projectURL = scm.userRemoteConfigs[0].url
            echo "Source code hosted in: ${projectURL}"
        }


        try {
            def credentialsIdPPCArray = scm.userRemoteConfigs.credentialsId
            credentialsIdPPC = credentialsIdPPCArray.first()
            echo "Using credentialsIdPPCDefault value for access to Parallel Project Configuration (PPC)"

        } catch (exc) {
            echo 'There is an error on retrieving credentialsId of multibranch configuration'
            def exc_message = exc.message
            echo "${exc_message}"

            credentialsIdPPC = credentialsIdPPCDefault
        }

        echo "credentialsIdPPC: ${credentialsIdPPC}"

        stage('Detect Node.js Parallel project configuration (PPC)') {

            packageJSON = readJSON file: 'package.json'

            packageName = packageJSON.name
            echo "packageName: ${packageName}"
            packageVersion = packageJSON.version
            echo "packageVersion: ${packageVersion}"
            packageTag = utils.getPackageTag(packageName, packageVersion)
            echo "packageTag: ${packageTag}"
            packageTarball = utils.getPackageTarball(packageName, packageVersion)
            echo "packageTarball: ${packageTarball}"
            isScopedPackage = utils.isScopedPackage(packageName)
            echo "isScopedPackage: ${isScopedPackage}"

            if (isScopedPackage) {
                packageScope = utils.getPackageScope(packageName)
                echo "packageScope: ${packageScope}"
            }

            try {
                def parallelConfigurationProject = utils.getParallelConfigurationProjectURL(projectURL)

                echo "Node.js parallel configuration project ${parallelConfigurationProject} searching"

                retry (3)
                        {
                            checkout([$class                           : 'GitSCM',
                                      branches                         : [[name: branchPPC]],
                                      doGenerateSubmoduleConfigurations: false,
                                      extensions                       : [[$class           : 'RelativeTargetDirectory',
                                                                           relativeTargetDir: relativeTargetDirPPC]],
                                      submoduleCfg                     : [],
                                      userRemoteConfigs                : [[credentialsId: credentialsIdPPC,
                                                                           url          : parallelConfigurationProject]]])
                        }

                echo "Node.js Parallel configuration project ${parallelConfigurationProject} exits"

                // Jenkinsfile
                isPPCJenkinsFile = fileExists jenkinsFilePathPPC

                if (isPPCJenkinsFile) {
                    echo "Node.js Parallel configuration project Jenkinsfile... FOUND"
                } else {
                    echo "Node.js Parallel configuration project Jenkinsfile... NOT FOUND"
                }


                // Jenkins.yml
                isPPCJenkinsYaml = fileExists jenkinsYamlPathPPC

                if (isPPCJenkinsYaml) {
                    echo "Node.js Parallel configuration project Jenkins.yml... FOUND"
                } else {
                    echo "Node.js Parallel configuration project Jenkins.yml... NOT FOUND"
                }

                // Openshift template (template.yaml)
                isPPCOpenshiftTemplate = fileExists openshiftNodejsTemplatePathPPC

                if (isPPCOpenshiftTemplate) {
                    echo "Node.js Parallel configuration project Openshift template... FOUND"
                } else {
                    echo "Node.js Parallel configuration project Openshift template... NOT FOUND"
                }


                echo "isPPCJenkinsFile : ${isPPCJenkinsFile}"
                echo "isPPCJenkinsYaml : ${isPPCJenkinsYaml}"
                echo "isPPCOpenshiftTemplate : ${isPPCOpenshiftTemplate}"

            }
            catch (exc) {
                echo 'There is an error on retrieving Node.js parallel project configuration'
                def exc_message = exc.message
                echo "${exc_message}"
            }
        }


        if (isPPCJenkinsFile) {

            stage('Switch to Node.js parallel configuration project Jenkinsfile') {

                echo "Loading Jenkinsfile from Node.js Parallel Configuration Project (PPC)"

                jenknsFilePipelinePPC = load jenkinsFilePathPPC

                echo "Jenkinsfile from Node.js Parallel Configuration Project (PPC) loaded"

                echo "Executing Jenkinsfile from Node.js Parallel Configuration Project (PPC)"

                jenknsFilePipelinePPC.runNodejsPPCJenkinsfile()
            }


        } else {
            echo "Executing Jenkinsfile from Node.js Generic Configuration Project (PGC)"

            stage('Load Node.js pipeline configuration') {

                if (isPPCJenkinsYaml && isPPCOpenshiftTemplate) {
                    //The generic pipeline will use Jenkins.yml and template of the parallel project configuration

                    //Take parameters of the parallel project configuration (PPC)
                    params = readYaml  file: jenkinsYamlPathPPC
                    echo "Using Jenkins.yml from Node.js parallel project configuration (PPC)"

                    //The template is provided by parallel project configuration (PPC)
                    params.openshift.templatePath = relativeTargetDirPPC + params.openshift.templatePath
                    echo "Template provided by Node.js parallel project configuration (PPC)"

                    assert params.openshift.templatePath?.trim()

                    echo "params.openshift.templatePath: ${params.openshift.templatePath}"

                } else {




        }

    } // end of node



    echo "END NODE.JS GENERIC CONFIGURATION PROJECT (PGC)"

} //end of method

return this;

