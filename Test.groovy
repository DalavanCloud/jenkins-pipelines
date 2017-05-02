#!groovy

node('master') {
    timestamps {
        stage('Checkout') {
            dir('rootspi') {
                git url: 'https://github.com/root-project/rootspi.git'
            }
        }

        stage('Generate reports') {
            step([$class: 'LogParserPublisher',
                    parsingRulesPath: "${pwd()}/rootspi/jenkins/logparser-rules/ROOT-incremental-LogParserRules.txt", 
                    useProjectRule: false, unstableOnWarning: false, failBuildOnError: true])
        }
    }
}
