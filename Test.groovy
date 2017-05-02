#!groovy

properties([
    parameters([
        string(name: 'ROOT_REFSPEC', defaultValue: '', description: 'Refspec for ROOT repository'),
        string(name: 'ROOTTEST_BRANCH', defaultValue: 'master', description: 'Name of the ROOT branch to work with'),
        string(name: 'ROOT_BRANCH', defaultValue: 'master', description: 'Name of the roottest branch to work with'),
        string(name: 'BUILD_NOTE', defaultValue: '', description: 'Note to add after label/compiler in job name'),
        string(name: 'BUILD_DESCRIPTION', defaultValue: '', description: 'Build description')
    ])
])


// Treat parameters as environment variables
for (ParameterValue p in params) {
    env[p.key] = p.value
}

// TODO: This should be avoided 
env.GIT_URL = 'https://github.com/root-project/root.git'

currentBuild.setDisplayName("#$BUILD_NUMBER ")
currentBuild.setDescription("$BUILD_DESCRIPTION")

node('slc6') {
    timestamps {
        stage('Checkout') {
            dir('root') {
                // TODO: Use the git step when it has implemented specifying refspecs
                checkout([$class: 'GitSCM', branches: [[name: ROOT_BRANCH]], doGenerateSubmoduleConfigurations: false, extensions: [],
                        submoduleCfg: [], userRemoteConfigs: [[refspec: ROOT_REFSPEC, url: env.GIT_URL]]])
            }

            dir('roottest') {
                git url: 'https://github.com/root-project/roottest.git', branch: ROOTTEST_BRANCH
            }

            dir('rootspi') {
                git url: 'https://github.com/root-project/rootspi.git'
            }
        }

        try {
            stage('Build') {
                //sh 'rootspi/jenkins/jk-all build'
            }

            stage('Test') {
                //sh 'rootspi/jenkins/jk-all test'

                def testThreshold = [[$class: 'FailedThreshold', 
                        failureNewThreshold: '0', failureThreshold: '0', unstableNewThreshold: '0', 
                        unstableThreshold: '0'], [$class: 'SkippedThreshold', failureNewThreshold: '', 
                        failureThreshold: '', unstableNewThreshold: '', unstableThreshold: '']]

                step([$class: 'XUnitBuilder', 
                        testTimeMargin: '3000', thresholdMode: 1, thresholds: testThreshold, 
                        tools: [[$class: 'CTestType', 
                                deleteOutputFiles: true, failIfNotNew: false, pattern: 'build/Testing/*/Test.xml', 
                                skipNoTestFiles: false, stopProcessingIfError: true]]])
                
                if (currentBuild.result == 'FAILURE') {
                    throw new Exception("Test result caused build to fail")
                }
            }
        } catch (err) {
            println 'Build failed because:'
            println err
            currentBuild.result = 'FAILURE'
        }

        stage('Generate reports') {
            step([$class: 'LogParserPublisher',
                    parsingRulesPath: "${pwd()}/rootspi/jenkins/logparser-rules/ROOT-incremental-LogParserRules.txt", 
                    useProjectRule: false, unstableOnWarning: false, failBuildOnError: true])
        }

        //stage('Archive environment') {
        // TODO: Bundle and store build env in here
            //archiveArtifacts artifacts: 'build/'
        //}
    }
}
