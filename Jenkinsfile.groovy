#!/usr/bin/groovy
import com.evobanco.NodejsUtils
import com.evobanco.NodejsConstants
import java.text.SimpleDateFormat
import java.util.Date

def runNodejsGenericJenkinsfile() {

    def utils = new NodejsUtils()

    def npmRepositoryURL = 'http://10.6.14.20:8081/artifactory/api/npm/npm-repo/'

    def sonarQube = 'http://sonarqube:9000'
    def openshiftURL = 'https://openshift.grupoevo.corp:8443'
    def openshiftCredential = 'openshift'
    def registry = '172.20.253.34'
    def artifactoryCredential = 'artifactory-token'
    def artifactoryNPMAuthCredential = 'artifactory-npm-auth'
    def artifactoryNPMEmailAuthCredential = 'artifactory-npm-email-auth'
    def jenkinsNamespace = 'cicd'
    def params
    String envLabel
    String branchName
    String branchNameHY
    String branchType


    //Parallel project configuration (PPC) properties
    def branchPPC = 'master'
    String credentialsIdPPCDefault = '4b18ea85-c50b-40f4-9a81-e89e44e20178' //credentials of the parallel configuration project
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
    String projectURL
    String packageName
    String packageVersion
    String packageTag
    String packageTarball
    boolean isScopedPackage = false
    String packageScope


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

    def nodeJS_8_installation = "Node-8.9.4"
    def nodeJS_6_installation = "Node-6.11.3"
    def nodeJS_pipeline_installation = ""
    int image_stream_nodejs_version = image_stream_nodejs_version_default

    def NPM_TOKEN_CREDENTIALS = "2631fdfc-50c2-458a-b257-8571a4038b38"
    def sonarProjectPath = "sonar-project.properties"

    echo "BEGIN NODE.JS GENERIC CONFIGURATION PROJECT (PGC)"


    node('nodejs') {

        echo 'Pipeline begin timestamp... '
        sh 'date'

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
                    //The Node.js generic pipeline will use Node.js generic Jenkins.yml or Node.js generic Openshift template
                    //We need load this elements

                    echo "Node.js generic configuration project loading"

                    retry (3) {
                        checkout([$class                           : 'GitSCM',
                                  branches                         : [[name: branchGenericPGC]],
                                  doGenerateSubmoduleConfigurations: false,
                                  extensions                       : [[$class           : 'RelativeTargetDirectory',
                                                                       relativeTargetDir: relativeTargetDirGenericPGC]],
                                  submoduleCfg                     : [],
                                  userRemoteConfigs                : [[credentialsId: credentialsIdGenericPGC,
                                                                       url          : gitDefaultProjectConfigurationPath]]])
                    }

                    echo "Node.js generic configuration project loaded"


                    if (isPPCJenkinsYaml) {
                        //Take parameters of the parallel project configuration (PPC)
                        params = readYaml  file: jenkinsYamlPathPPC
                        echo "Using Jenkins.yml from Node.js parallel project configuration (PPC)"
                    } else {
                        //Take the generic parameters
                        params = readYaml  file: jenkinsYamlGenericPath
                        echo "Using Jenkins.yml from Node.js generic project"
                    }

                    if (isPPCOpenshiftTemplate) {
                        //The template is provided by parallel project configuration (PPC)
                        params.openshift.templatePath = relativeTargetDirPPC + params.openshift.templatePath
                        echo "Template provided by Node.js parallel project configuration (PPC)"
                    } else {
                        //The tamplate is provided by generic configuration
                        params.openshift.templatePath = relativeTargetDirGenericPGC + params.openshift.templatePath
                        echo "Template provided by Node.js generic configuration project"
                    }

                    assert params.openshift.templatePath?.trim()

                    echo "params.openshift.templatePath: ${params.openshift.templatePath}"
                }

            }

            stage('Node initialize') {
                echo 'Node initializing...'

                /*************************************************************
                 ************* IMAGE STREAM TAG NODE VERSION *****************
                 *************************************************************/
                echo "params.imageStreamNodejsVersion: ${params.imageStreamNodejsVersion}"

                String imageStreamNodejsVersionParam = params.imageStreamNodejsVersion
                if (imageStreamNodejsVersionParam != null && imageStreamNodejsVersionParam.isInteger()) {
                    image_stream_nodejs_version = imageStreamNodejsVersionParam as Integer
                }

                if (image_stream_nodejs_version >= 8) {
                    echo "Assigning NodeJS installation ${nodeJS_8_installation}"
                    nodeJS_pipeline_installation = nodeJS_8_installation
                } else if (image_stream_nodejs_version >= 6) {
                    echo "Assigning NodeJS installation ${nodeJS_6_installation}"
                    nodeJS_pipeline_installation = nodeJS_6_installation
                } else {
                    currentBuild.result = "FAILED"
                    throw new hudson.AbortException("Error checking existence of package on NPM registry")
                }

                def node = tool name: "${nodeJS_pipeline_installation}", type: 'jenkins.plugins.nodejs.tools.NodeJSInstallation'
                env.PATH = "${node}/bin:${env.PATH}"

                echo 'Node version:'
                sh "node -v"

                echo 'NPM version:'
                sh "npm -v"
            }

            stage('Configure Artifactory NPM Registry') {
                echo 'Setting Artifactory NPM registry'
                withCredentials([string(credentialsId: "${artifactoryNPMAuthCredential}", variable: 'ARTIFACTORY_NPM_AUTH')]) {
                    withCredentials([string(credentialsId: "${artifactoryNPMEmailAuthCredential}", variable: 'ARTIFACTORY_NPM_EMAIL_AUTH')]) {
                        withEnv(["NPM_AUTH=${ARTIFACTORY_NPM_AUTH}", "NPM_AUTH_EMAIL=${ARTIFACTORY_NPM_EMAIL_AUTH}"]) {
                            withNPM(npmrcConfig: 'my-custom-npmrc') {
                                sh "npm config set registry ${npmRepositoryURL} "
                            }
                        }
                    }
                }
            }



            if (branchType in params.npmRegistryDeploy) {
                stage('TEST Artifactory NPM registry credentials') {
                    echo 'Try credentials'
                    withCredentials([string(credentialsId: "${artifactoryNPMAuthCredential}", variable: 'ARTIFACTORY_NPM_AUTH')]) {
                        withCredentials([string(credentialsId: "${artifactoryNPMEmailAuthCredential}", variable: 'ARTIFACTORY_NPM_EMAIL_AUTH')]) {
                            withEnv(["NPM_AUTH=${ARTIFACTORY_NPM_AUTH}", "NPM_AUTH_EMAIL=${ARTIFACTORY_NPM_EMAIL_AUTH}"]) {
                                withNPM(npmrcConfig: 'my-custom-npmrc') {
                                    echo 'Get config registry'
                                    sh 'npm config get registry'

                                    echo 'Test npm repository authentication'
                                    sh 'npm whoami'
                                }
                            }
                        }
                    }
                }
            }


            stage('Prepare') {
                echo "Prepare stage (PGC)"

                nodejsSetDisplayName()

                echo "${currentBuild.displayName}"

                branchName = utils.getBranch()
                echo "We are on branch ${branchName}"
                branchType = utils.getBranchType(branchName)
                echo "This branch is a ${branchType} branch"
                branchNameHY = branchName.replace("/", "-").replace(".", "-").replace("_","-")
                echo "Branch name processed: ${branchName}"

            }

            stage ('Openshift environment') {
                switch (branchType) {
                    case 'feature':
                        echo "Detect feature type branch"
                        envLabel="dev"
                        break
                    case 'develop':
                        echo "Detect develop type branch"
                        envLabel="dev"
                        break
                    case 'release':
                        echo "Detect release type branch"
                        envLabel="uat"
                        break
                    case 'master':
                        echo "Detect master type branch"
                        envLabel="pro"
                        break
                    case 'hotfix':
                        echo "Detect hotfix type branch"
                        envLabel="uat"
                        break
                }
                echo "Environment selected: ${envLabel}"
            }

            if (branchName != 'master') {

                stage('Build') {
                    echo 'Building dependencies...'

                    withCredentials([string(credentialsId: "${artifactoryNPMAuthCredential}", variable: 'ARTIFACTORY_NPM_AUTH')]) {
                        withCredentials([string(credentialsId: "${artifactoryNPMEmailAuthCredential}", variable: 'ARTIFACTORY_NPM_EMAIL_AUTH')]) {
                            withEnv(["NPM_AUTH=${ARTIFACTORY_NPM_AUTH}", "NPM_AUTH_EMAIL=${ARTIFACTORY_NPM_EMAIL_AUTH}"]) {
                                withNPM(npmrcConfig: 'my-custom-npmrc') {
                                    sh 'npm i'
                                }
                            }
                        }
                    }
                }

                if (branchType in params.testing.predeploy.unitTesting) {
                    stage('Test') {

                        withCredentials([string(credentialsId: "${artifactoryNPMAuthCredential}", variable: 'ARTIFACTORY_NPM_AUTH')]) {
                            withCredentials([string(credentialsId: "${artifactoryNPMEmailAuthCredential}", variable: 'ARTIFACTORY_NPM_EMAIL_AUTH')]) {
                                withEnv(["NPM_AUTH=${ARTIFACTORY_NPM_AUTH}", "NPM_AUTH_EMAIL=${ARTIFACTORY_NPM_EMAIL_AUTH}"]) {
                                    echo 'Installing jest'
                                    withNPM(npmrcConfig: 'my-custom-npmrc') {
                                        sh 'npm i -D jest'
                                    }

                                    echo 'Installing jest-sonar-reporter'
                                    withNPM(npmrcConfig: 'my-custom-npmrc') {
                                        sh 'npm i -D jest-sonar-reporter'
                                    }

                                    echo 'Testing...'
                                    withNPM(npmrcConfig: 'my-custom-npmrc') {
                                        sh 'npm test'
                                    }
                                }
                            }
                        }

                    }
                } else {
                    echo "Skipping unit tests..."
                }


                if (branchType in params.testing.predeploy.sonarQube) {

                    stage('SonarQube') {
                        echo "Running SonarQube..."


                        // Jenkinsfile
                        isSonarProjectFile = fileExists sonarProjectPath
                        echo "isSonarProjectFile : ${isSonarProjectFile}"

                        def sonar_project_key = packageName + "-" + branchNameHY
                        def sonar_project_name = packageName + "-" + branchNameHY

                        echo "sonar_project_key: ${sonar_project_key}"
                        echo "sonar_project_name: ${sonar_project_name}"

                        // requires SonarQube Scanner 3.1+
                        def scannerHome = tool 'SonarQube Scanner 3.1.0'

                        if (isSonarProjectFile) {
                            //sonar-project.properties contains properties for SonarQube

                            echo 'sonarQube parameters extracted from sonar-project.properties file'

                            withSonarQubeEnv('sonarqube') {
                                sh "${scannerHome}/bin/sonar-scanner -X -Dsonar.projectKey=${sonar_project_key} -Dsonar.projectName=${sonar_project_name}"
                            }

                        } else {

                            if (params.testing.predeploy.sonarQubeAnalisis.sonarSources
                                && params.testing.predeploy.sonarQubeAnalisis.sonarTests
                                && params.testing.predeploy.sonarQubeAnalisis.sonarTestExecutionReportPath
                                && params.testing.predeploy.sonarQubeAnalisis.sonarCoverageReportPath
                                && params.testing.predeploy.sonarQubeAnalisis.sonarExclusions) {

                                //Pipeline parameters contains properties for SonarQube.
                                def sonarSources = params.testing.predeploy.sonarQubeAnalisis.sonarSources
                                def sonarTests = params.testing.predeploy.sonarQubeAnalisis.sonarTests
                                def sonarTestExecutionReportPath = params.testing.predeploy.sonarQubeAnalisis.sonarTestExecutionReportPath
                                def sonarCoverageReportPath = params.testing.predeploy.sonarQubeAnalisis.sonarCoverageReportPath
                                def sonarExclusions = params.testing.predeploy.sonarQubeAnalisis.sonarExclusions

                                echo 'sonarQube parameters extracted from pipeline parameters:'

                                echo "sonarSources: ${sonarSources}"
                                echo "sonarTests: ${sonarTests}"
                                echo "sonarTestExecutionReportPath: ${sonarTestExecutionReportPath}"
                                echo "sonarCoverageReportPath: ${sonarCoverageReportPath}"
                                echo "sonarExclusions: ${sonarExclusions}"

                                withSonarQubeEnv('sonarqube') {
                                    sh "${scannerHome}/bin/sonar-scanner -X -Dsonar.projectKey=${sonar_project_key} -Dsonar.projectName=${sonar_project_name} -Dsonar.sources=${sonarSources} -Dsonar.tests=${sonarTests} -Dsonar.testExecutionReportPaths=${sonarTestExecutionReportPath} -Dsonar.javascript.lcov.reportPaths=${sonarCoverageReportPath} -Dsonar.exclusions=${sonarExclusions}"
                                }

                            } else {
                                //Failed status
                                currentBuild.result = NodejsConstants.FAILURE_BUILD_RESULT
                                throw new hudson.AbortException("A mandatory sonarQube parameter has not found. A sonar-project.properties OR sonarQube pipeline parameters are mandatory. The mandatory properties on sonar-project.properties are sonar.sources, sonar.tests, sonar.testExecutionReportPaths, sonar.javascript.lcov.reportPaths and sonar.exclusions. The mandatory params.testing.predeploy.sonarQubeAnalisis parameters of pipeline are: sonarSources, sonarTests, sonarTestExecutionReportPath. sonarCoverageReportPath amd sonarExclusions")

                            }

                        }

                    }

                } else {
                    echo "Skipping Running SonarQube..."
                }


                if (branchType in params.npmRegistryDeploy) {

                    stage('Artifact Registry Publish') {
                        echo "Publishing artifact to a NPM registry"

                        withCredentials([string(credentialsId: "${artifactoryNPMAuthCredential}", variable: 'ARTIFACTORY_NPM_AUTH')]) {
                            withCredentials([string(credentialsId: "${artifactoryNPMEmailAuthCredential}", variable: 'ARTIFACTORY_NPM_EMAIL_AUTH')]) {
                                withEnv(["NPM_AUTH=${ARTIFACTORY_NPM_AUTH}", "NPM_AUTH_EMAIL=${ARTIFACTORY_NPM_EMAIL_AUTH}"]) {
                                    withNPM(npmrcConfig: 'my-custom-npmrc') {

                                        echo 'Get config registry'
                                        sh 'npm config get registry'

                                        echo 'Test npm repository authentication'
                                        sh 'npm whoami'

                                        echo 'Publish package on Artifactory NPM registry'
                                        //sh 'npm publish'
                                    }
                                }
                            }
                        }

                        echo "Setting source code to build from URL (build from registry package)"
                        echo "Source URL: ${projectURL} --> ${build_from_registry_url}"
                        projectURL = build_from_registry_url
                        echo "new projectURL: ${projectURL}"
                        echo "Setting source code to build from branch (build from registry package)"
                        echo "Source branch: ${branchName} --> ${build_from_artifact_branch}"
                        branchName = build_from_artifact_branch
                        echo "new branchName: ${branchName}"
                    }

                } else {
                    echo "******* WARNING. PACKAGE NOT PUBLISHED ON ANY NPM REGISTRY ******* "
                    echo "The source code will be taken from a code repository, not from an artifact repository."
                    echo "Source URL: ${projectURL}"
                    echo "Source branch: ${branchName}"
                }

            } else {

                if (branchType in params.npmRegistryDeploy) {
                    stage('Check published package on NPM registry') {

                        try {
                            echo 'Get tarball location of package ...'
                            tarball_script = $/eval "npm view  ${packageTag} dist.tarball | grep '${packageTarball}'"/$
                            echo "${tarball_script}"
                            def tarball_view = sh(script: "${tarball_script}", returnStdout: true).toString().trim()
                            echo "${tarball_view}"
                        } catch (exc) {
                            echo 'There is an error on retrieving the tarball location'
                            def exc_message = exc.message
                            echo "${exc_message}"
                            currentBuild.result = "FAILED"
                            throw new hudson.AbortException("Error checking existence of package on NPM registry")
                        }
                    }
                }
            }

            stage('OpenShift Build') {

                /********************************************************
                 ************* SPECIFIC PORT PARAMETERS *****************
                 ********************************************************/
                Boolean useSpecificPort = false
                int port_number = port_default
                Boolean createPortEnvironmentVariable = false
                echo "params.ports.useSpecificPort: ${params.ports.useSpecificPort}"
                echo "params.ports.portNumber: ${params.ports.portNumber}"
                echo "params.ports.createPortEnvironmentVariable: ${params.ports.createPortEnvironmentVariable}"


                if (params.ports.useSpecificPort) {
                    useSpecificPort = params.ports.useSpecificPort.toBoolean()
                }

                String portNumberParam = params.ports.portNumber
                if (portNumberParam != null && portNumberParam.isInteger() && useSpecificPort) {
                    port_number = portNumberParam as Integer
                }


                if (params.ports.createPortEnvironmentVariable && useSpecificPort) {
                    createPortEnvironmentVariable = params.ports.createPortEnvironmentVariable.toBoolean()
                }

                echo "useSpecificPort: ${useSpecificPort}"
                echo "port_number: ${port_number}"
                echo "createPortEnvironmentVariable: ${createPortEnvironmentVariable}"


                /***************************************************
                 ************* DEV MODE PARAMETERS *****************
                 ***************************************************/
                Boolean devMode = false
                int debug_port_number = debug_port_default
                echo "params.devMode: ${params.devMode}"
                echo "params.debugPort: ${params.debugPort}"

                if (params.devMode) {
                    devMode = params.devMode.toBoolean()
                }

                String debugPortParam = params.debugPort

                if (debugPortParam != null && debugPortParam.isInteger() && devMode) {
                    debug_port_number = debugPortParam as Integer
                }

                echo "devMode: ${devMode}"
                echo "debug_port_number: ${debug_port_number}"

                /***************************************************
                 ************* NPM MIRROR PARAMETERS *****************
                 ***************************************************/
                Boolean useNpmMirror = false
                def theNpmMirror = ""
                echo "params.useNpmMirror: ${params.useNpmMirror}"
                echo "params.npmMirror: ${params.npmMirror}"

                if (params.useNpmMirror) {
                    useNpmMirror = params.useNpmMirror.toBoolean()
                }

                if (useNpmMirror) {
                    theNpmMirror = params.npmMirror
                }

                echo "useNpmMirror: ${useNpmMirror}"
                echo "theNpmMirror: ${theNpmMirror}"

                /*******************************************************************
                 ************* NPM RUN ALTERNATE SCRIPT PARAMETERS *****************
                 *******************************************************************/
                Boolean useAlternateNpmRun = false
                def alternateNpmRunScript = ''
                echo "params.useAlternateNpmRun: ${params.useAlternateNpmRun}"
                echo "params.alternateNpmRunScript: ${params.alternateNpmRunScript}"

                if (params.useAlternateNpmRun) {
                    useAlternateNpmRun = params.useAlternateNpmRun.toBoolean()
                }

                if (useAlternateNpmRun) {
                    alternateNpmRunScript = params.alternateNpmRunScript
                }

                echo "useAlternateNpmRun: ${useAlternateNpmRun}"
                echo "alternateNpmRunScript: ${alternateNpmRunScript}"

                /**********************************************************
                 ************* OPENSHIFT PROJECT CREATION *****************
                 **********************************************************/

                echo "Building image on OpenShift..."

                //def my_sourceRepositoryURL = "https://github.com/isanmartin0/nodejs-helloWorld"
                //def my_sourceRepositoryBranch = "release/1.0.3"

                nodejsOpenshiftCheckAndCreateProject {
                    oseCredential = openshiftCredential
                    cloudURL = openshiftURL
                    environment = envLabel
                    jenkinsNS = jenkinsNamespace
                    artCredential = artifactoryCredential
                    template = openshiftNodejsTemplateGenericPath
                    branchHY = branchNameHY
                    branch_type = branchType
                    dockerRegistry = registry
                    sourceRepositoryURL = projectURL
                    sourceRepositoryBranch = branchName
                    portNumber = port_number
                    nodejsVersion = image_stream_nodejs_version
                    package_tag = packageTag
                    package_tarball = packageTarball
                    is_scoped_package = isScopedPackage
                }



                /**************************************************************
                 ************* ENVIRONMENT VARIABLES CREATION *****************
                 **************************************************************/

                echo "Creating environment variables"
                def mapEnvironmentVariables = [:]

                echo "params.environmentVariables:"
                params.environmentVariables.each { key, value ->
                    echo "params environment variable: ${key} = ${value}"
                }

                if (params.environmentVariables) {
                    mapEnvironmentVariables = params.environmentVariables
                }

                echo "mapEnvironmentVariables:"
                mapEnvironmentVariables.each { key, value ->
                    echo "Map environment variable: ${key} = ${value}"
                }

                retry(3) {
                    nodejsOpenshiftEnvironmentVariables {
                        branchHY = branchNameHY
                        branch_type = branchType
                        createPortEnvironmentVariableOpenshift = createPortEnvironmentVariable
                        portNumber = port_number
                        devModeOpenshift = devMode
                        debugPortOpenshift = debug_port_number
                        useNpmMirrorOpenshift = useNpmMirror
                        npmMirrorOpenshift = theNpmMirror
                        useAlternateNpmRunOpenshift = useAlternateNpmRun
                        alternateNpmRunScriptOpenshift = alternateNpmRunScript
                        map_environment_variables = mapEnvironmentVariables
                    }

                    sleep(10)
                }


                nodejsOpenshiftBuildProject {
                    repoUrl = npmRepositoryURL
                    branchHY = branchNameHY
                    branch_type = branchType
                    devModeOpenshift = devMode
                    debugPortOpenshift = debug_port_number
                    useNpmMirrorOpenshift = useNpmMirror
                    npmMirrorOpenshift = theNpmMirror
                    useAlternateNpmRunOpenshift = useAlternateNpmRun
                    alternateNpmRunScriptOpenshift = alternateNpmRunScript
                }
            }

        }

    } // end of node

    if (!isPPCJenkinsFile) {
        def deploy = 'Yes'

        if (branchType in params.confirmDeploy) {
            try {
                stage('Decide on Deploying') {

                    //Parameters timeout deploy answer

                    Boolean timeoutConfirmDeploy = false
                    int timeoutConfirmDeployTime = 0
                    String timeoutConfirmDeployUnit = ''
                    boolean isTimeoutConfirmDeployUnitValid = false

                    echo "params.timeoutConfirmDeploy: ${params.timeoutConfirmDeploy}"

                    if (params.timeoutConfirmDeploy != null) {
                        timeoutConfirmDeploy = params.timeoutConfirmDeploy.toBoolean()
                    }

                    if (timeoutConfirmDeploy) {
                        echo "params.timeoutConfirmDeployTime: ${params.timeoutConfirmDeployTime}"
                        echo "params.timeoutConfirmDeployUnit: ${params.timeoutConfirmDeployUnit}"

                        String timeoutConfirmDeployTimeParam = params.timeoutConfirmDeployTime
                        if (timeoutConfirmDeployTimeParam != null && timeoutConfirmDeployTimeParam.isInteger()) {
                            timeoutConfirmDeployTime = timeoutConfirmDeployTimeParam as Integer
                        }

                        if (params.timeoutConfirmDeployUnit != null && ("NANOSECONDS".equals(params.timeoutConfirmDeployUnit.toUpperCase())
                                || "MICROSECONDS".equals(params.timeoutConfirmDeployUnit.toUpperCase())
                                || "MILLISECONDS".equals(params.timeoutConfirmDeployUnit.toUpperCase())
                                || "SECONDS".equals(params.timeoutConfirmDeployUnit.toUpperCase())
                                || "MINUTES".equals(params.timeoutConfirmDeployUnit.toUpperCase())
                                || "HOURS".equals(params.timeoutConfirmDeployUnit.toUpperCase())
                                || "DAYS".equals(params.timeoutConfirmDeployUnit.toUpperCase()))) {
                            isTimeoutConfirmDeployUnitValid = true
                            timeoutConfirmDeployUnit = params.timeoutConfirmDeployUnit.toUpperCase()
                        }
                    }

                    echo "timeoutConfirmDeploy value: ${timeoutConfirmDeploy}"

                    if (timeoutConfirmDeploy) {
                        echo "timeoutConfirmDeployTime value: ${timeoutConfirmDeployTime}"
                        echo "timeoutConfirmDeployUnit value: ${timeoutConfirmDeployUnit}"
                    }


                    if (timeoutConfirmDeploy && timeoutConfirmDeployTime > 0 && isTimeoutConfirmDeployUnitValid) {
                        //Wrap input with timeout
                        timeout(time:timeoutConfirmDeployTime, unit:"${timeoutConfirmDeployUnit}") {
                            deploy = input message: 'Waiting for user approval',
                                    parameters: [choice(name: 'Continue and deploy?', choices: 'No\nYes', description: 'Choose "Yes" if you want to deploy this build')]
                        }
                    } else {
                        //Input without timeout
                        deploy = input message: 'Waiting for user approval',
                                parameters: [choice(name: 'Continue and deploy?', choices: 'No\nYes', description: 'Choose "Yes" if you want to deploy this build')]

                    }
                }
            } catch (err) {
                def user = err.getCauses()[0].getUser()
                if('SYSTEM'.equals(user.toString())) { //timeout
                    currentBuild.result = "FAILED"
                    throw new hudson.AbortException("Timeout on confirm deploy")
                }
            }
        }

        if (deploy == 'Yes') {
            node {
                checkout scm
                stage('OpenShift Deploy') {
                    echo "Deploying on OpenShift..."

                    openshift_route_hostname = nodejsOpenshiftDeployProject {
                        branchHY = branchNameHY
                        branch_type = branchType
                    }

                    openshift_route_hostname_with_protocol = utils.getRouteHostnameWithProtocol(openshift_route_hostname, false)

                }
            }

            echo "Openshift route hostname: ${openshift_route_hostname}"
            echo "Openshift route hostname (with protocol): ${openshift_route_hostname_with_protocol}"

            echo "params.jenkins.errorOnPostDeployTestsUnstableResult: ${params.jenkins.errorOnPostDeployTestsUnstableResult}"
            Boolean errorOnPostDeployTestsUnstableResult = false

            if (params.jenkins.errorOnPostDeployTestsUnstableResult != null) {
                errorOnPostDeployTestsUnstableResult = params.jenkins.errorOnPostDeployTestsUnstableResult.toBoolean()
            }

            echo "errorOnPostDeployTestsUnstableResult value: ${errorOnPostDeployTestsUnstableResult}"

            def tasks = [:]

            //Smoke tests
            if (branchType in params.testing.postdeploy.smokeTesting) {
                tasks["${NodejsConstants.SMOKE_TEST_TYPE}"] = {
                    node('taurus') { //taurus
                        try {
                            stage("${NodejsConstants.SMOKE_TEST_TYPE} Tests") {
                                nodejsExecutePerformanceTest {
                                    pts_taurus_test_base_path = taurus_test_base_path
                                    pts_acceptance_test_path = smoke_test_path
                                    pts_openshift_route_hostname_with_protocol = openshift_route_hostname_with_protocol
                                    pts_performance_test_type = NodejsConstants.SMOKE_TEST_TYPE
                                }
                            }
                        } catch (exc) {
                            def exc_message = exc.message
                            echo "${exc_message}"
                            if (errorOnPostDeployTestsUnstableResult) {
                                currentBuild.result = NodejsConstants.UNSTABLE_BUILD_RESULT
                            } else {
                                //Failed status
                                currentBuild.result = NodejsConstants.FAILURE_BUILD_RESULT
                                throw new hudson.AbortException("The ${NodejsConstants.SMOKE_TEST_TYPE} tests stage has failures")
                            }
                        }
                    }
                }
            } else {
                echo "Skipping ${NodejsConstants.SMOKE_TEST_TYPE} tests..."
            }

            //Acceptance tests
            if (branchType in params.testing.postdeploy.acceptanceTesting) {
                tasks["${NodejsConstants.ACCEPTANCE_TEST_TYPE}"] = {
                    node('taurus') { //taurus
                        try {
                            stage("${NodejsConstants.ACCEPTANCE_TEST_TYPE} Tests") {
                                nodejsExecutePerformanceTest {
                                    pts_taurus_test_base_path = taurus_test_base_path
                                    pts_acceptance_test_path = acceptance_test_path
                                    pts_openshift_route_hostname_with_protocol = openshift_route_hostname_with_protocol
                                    pts_performance_test_type = NodejsConstants.ACCEPTANCE_TEST_TYPE
                                }
                            }
                        } catch (exc) {
                            def exc_message = exc.message
                            echo "${exc_message}"
                            if (errorOnPostDeployTestsUnstableResult) {
                                currentBuild.result = NodejsConstants.UNSTABLE_BUILD_RESULT
                            } else {
                                //Failed status
                                currentBuild.result = NodejsConstants.FAILURE_BUILD_RESULT
                                throw new hudson.AbortException("The ${NodejsConstants.ACCEPTANCE_TEST_TYPE} tests stage has failures")
                            }
                        }
                    }
                }
            } else {
                echo "Skipping ${NodejsConstants.ACCEPTANCE_TEST_TYPE} tests..."
            }

            //Security tests
            if (branchType in params.testing.postdeploy.securityTesting) {
                tasks["${NodejsConstants.SECURITY_TEST_TYPE}"] = {
                    node('taurus') { //taurus
                        try {
                            stage("${NodejsConstants.SECURITY_TEST_TYPE} Tests") {
                                nodejsExecutePerformanceTest {
                                    pts_taurus_test_base_path = taurus_test_base_path
                                    pts_acceptance_test_path = security_test_path
                                    pts_openshift_route_hostname_with_protocol = openshift_route_hostname_with_protocol
                                    pts_performance_test_type = NodejsConstants.SECURITY_TEST_TYPE
                                }
                            }
                        } catch (exc) {
                            def exc_message = exc.message
                            echo "${exc_message}"
                            if (errorOnPostDeployTestsUnstableResult) {
                                currentBuild.result = NodejsConstants.UNSTABLE_BUILD_RESULT
                            } else {
                                //Failed status
                                currentBuild.result = NodejsConstants.FAILURE_BUILD_RESULT
                                throw new hudson.AbortException("The ${NodejsConstants.SECURITY_TEST_TYPE} tests stage has failures")
                            }
                        }
                    }
                }
            } else {
                echo "Skipping ${NodejsConstants.SECURITY_TEST_TYPE} tests..."
            }


            //Executing smoke, acceptance and security tests in parallel
            parallel tasks


            //Performance tests
            if (branchType in params.testing.postdeploy.performanceTesting) {
                node('taurus') { //taurus
                    try {
                        stage("${NodejsConstants.PERFORMANCE_TEST_TYPE} Tests") {
                            nodejsExecutePerformanceTest {
                                pts_taurus_test_base_path = taurus_test_base_path
                                pts_acceptance_test_path = performance_test_path
                                pts_openshift_route_hostname_with_protocol = openshift_route_hostname_with_protocol
                                pts_performance_test_type = NodejsConstants.PERFORMANCE_TEST_TYPE
                            }
                        }
                    } catch (exc) {
                        def exc_message = exc.message
                        echo "${exc_message}"
                        if (errorOnPostDeployTestsUnstableResult) {
                            currentBuild.result = NodejsConstants.UNSTABLE_BUILD_RESULT
                        } else {
                            //Failed status
                            currentBuild.result = NodejsConstants.FAILURE_BUILD_RESULT
                            throw new hudson.AbortException("The ${NodejsConstants.PERFORMANCE_TEST_TYPE} tests stage has failures")
                        }
                    }
                }
            } else {
                echo "Skipping ${NodejsConstants.PERFORMANCE_TEST_TYPE} tests..."
            }

        } else {
            //User doesn't want to deploy
            //Failed status
            currentBuild.result = NodejsConstants.FAILURE_BUILD_RESULT
            throw new hudson.AbortException("The deploy on Openshift hasn't been confirmed")
        }



        stage('Notification') {
            echo "Sending Notifications..."

        }

        stage('Remove old builds') {

            echo "params.maxOldBuildsToKeep: ${params.jenkins.maxOldBuildsToKeep}"
            echo "params.daysOldBuildsToKeep: ${params.jenkins.daysOldBuildsToKeep}"

            String maxOldBuildsToKeepParam = params.jenkins.maxOldBuildsToKeep
            String daysOldBuildsToKeepParam = params.jenkins.daysOldBuildsToKeep

            if (maxOldBuildsToKeepParam != null && maxOldBuildsToKeepParam.isInteger()) {
                maxOldBuildsToKeep = maxOldBuildsToKeepParam as Integer
            }

            if (daysOldBuildsToKeepParam != null && daysOldBuildsToKeepParam.isInteger()) {
                daysOldBuildsToKeep = daysOldBuildsToKeepParam as Integer
            }

            echo "maxOldBuildsToKeep: ${maxOldBuildsToKeep}"
            echo "daysOldBuildsToKeep: ${daysOldBuildsToKeep}"

            if (maxOldBuildsToKeep > 0 && daysOldBuildsToKeep > 0) {

                echo "Keeping last ${maxOldBuildsToKeep} builds"
                echo "Keeping builds for  ${daysOldBuildsToKeep} last days"

                properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: "${daysOldBuildsToKeep}", numToKeepStr: "${maxOldBuildsToKeep}"]]])

            } else if (maxOldBuildsToKeep > 0) {

                echo "Keeping last ${maxOldBuildsToKeep} builds"

                properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: "${maxOldBuildsToKeep}"]]])

            } else if (daysOldBuildsToKeep > 0) {

                echo "Keeping builds for  ${daysOldBuildsToKeep} last days"

                properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: "${daysOldBuildsToKeep}", numToKeepStr: '']]])

            } else {

                echo "Not removing old builds."

                properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '']]])

            }

        }

    }

    node {

        echo "END NODE.JS GENERIC CONFIGURATION PROJECT (PGC)"

        echo 'Pipeline end timestamp... '
        sh 'date'

        echo "Current build duration: ${currentBuild.durationString.replace(' and counting', '')}"

    }



} //end of method

return this

