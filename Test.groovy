node('master') {
    
    dir('rootspi') {
        git url: 'https://github.com/root-project/rootspi'
    }
    sh "cat consoleText.txt"
    sh "echo 'end of log'"
    
    

    step([$class: 'LogParserPublisher',
            parsingRulesPath: "${pwd()}/rootspi/jenkins/logparser-rules/ROOT-incremental-LogParserRules.txt", 
            useProjectRule: false, unstableOnWarning: false, failBuildOnError: true])

}