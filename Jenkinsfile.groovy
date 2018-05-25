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
    def openshiftNodejsTemplatePathPPC = relativeTargetDirPPC + 'kube/Nodejs_template.yaml'
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
                def parallelConfigurationProject = utils.getParallelConfigurationProjectURL(projectURL, moduleName)

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
                isPPCOpenshiftTemplate = fileExists openshiftTemplatePathPPC

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

            stage('Initialize') {
                echo 'Initializing...'
                def node = tool name: 'Node-9.5.0', type: 'jenkins.plugins.nodejs.tools.NodeJSInstallation'
                env.PATH = "${node}/bin:${env.PATH}"

                sh "node -v"

            }

            stage('TEST npm whoami') {
                withNPM(npmrcConfig: 'my-custom-npmrc') {
                    sh 'npm whoami'
                }
            }


            stage('Prepare') {
                echo "Prepare stage (PGC)"

                setDisplayName()

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

                    withNPM(npmrcConfig: 'my-custom-npmrc') {
                        sh 'npm i'
                    }
                }

                if (branchType in params.testing.predeploy.unitTesting) {
                    stage('Test') {

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

/*
                        echo 'Publishing Test Coverage...'
                        		publishHTML (target: [
                        			allowMissing: false,
                        			alwaysLinkToLastBuild: false,
                        			keepAll: true,
                        			reportDir: 'coverage/lcov-report',
                        			reportFiles: 'index.html',
                        			reportName: "Application Test Coverage"
                        		])
*/
                    }
                } else {
                    echo "Skipping unit tests..."
                }


                if (branchType in params.testing.predeploy.sonarQube) {

                    stage('SonarQube') {
                        echo "Running SonarQube..."

                        def sonar_project_key = packageName + "-" + branchNameHY
                        def sonar_project_name = packageName + "-" + branchNameHY

                        echo "sonar_project_key: ${sonar_project_key}"
                        echo "sonar_project_name: ${sonar_project_name}"

                        echo "Replacing sonar.project.key and sonar.project.name variables with real values"
                        sh "sed -i -e 's/{sonarProjectKeyPlaceholder}/${sonar_project_key}/g' sonar-project.properties"
                        sh "sed -i -e 's/{sonarProjectNamePlaceholder}/${sonar_project_name}/g' sonar-project.properties"

                        // requires SonarQube Scanner 3.1+
                        def scannerHome = tool 'SonarQube Scanner 3.1.0';
                        withSonarQubeEnv('sonarqube') {
                            sh "${scannerHome}/bin/sonar-scanner -X"
                        }
                    }

                } else {
                    echo "Skipping Running SonarQube..."
                }


                if (branchType in params.npmRegistryDeploy) {

                    stage('Artifact Registry Publish') {
                        echo "Publishing artifact to a NPM registry"
                        withNPM(npmrcConfig: 'my-custom-npmrc') {
                            echo 'npm whoami'
                            sh 'npm whoami'
                            echo 'npm config registry'
                            sh 'npm config get registry'
                            //sh 'npm publish'
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

                stage('Check published package on Artifactory') {

                    //Get the location of the tarball
                    sh 'tarball_location=$(npm view "${ddd}" dist.tarball)'


                    //TODO: Completar check

                }



            }

        }

    } // end of node


    echo "END NODE.JS GENERIC CONFIGURATION PROJECT (PGC)"

} //end of method

return this;

