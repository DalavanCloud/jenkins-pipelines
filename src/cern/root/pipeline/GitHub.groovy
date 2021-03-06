package cern.root.pipeline

import hudson.plugins.logparser.LogParserAction
import hudson.tasks.junit.TestResultAction
import jenkins.model.Jenkins
import org.jenkinsci.plugins.ghprb.GhprbTrigger
import org.kohsuke.github.GHCommitState

/**
 * Facade towards GitHub.
 */
class GitHub implements Serializable {
    private def script
    private def parentJob
    private def repo
    private def prId
    private def sha1

    /**
     * Initialized a new GitHub facade.
     * @param script Script context.
     * @param parentJob The job to read the GitHub auth from.
     * @param repo Repository used for this build.
     * @param prId The pull request ID for this build.
     * @param sha1 Sha1 for the commit that triggered this build.
     */
    GitHub(script, parentJob, repo, prId, sha1) {
        this.script = script
        this.parentJob = parentJob
        this.repo = repo
        this.prId = prId
        this.sha1 = sha1
    }

    /**
     * Sets the commit status of this current build to failure.
     * @param statusText Status text to add on GitHub.
     */
    void setFailedCommitStatus(statusText) {
        setCommitStatus(GHCommitState.FAILURE, statusText)
    }

    /**
     * Sets the commit status of this current build to success.
     * @param statusText Status text to add on GitHub.
     */
    void setSucceedCommitStatus(statusText) {
        setCommitStatus(GHCommitState.SUCCESS, statusText)
    }

    /**
     * Sets the commit status of this build to pending.
     * @param statusText Status text to add on GitHub.
     */
    void setPendingCommitStatus(statusText) {
        setCommitStatus(GHCommitState.PENDING, statusText)
    }

    @NonCPS
    private void setCommitStatus(status, statusText) {
        def triggerJob = script.manager.hudson.getJob(parentJob)
        def prbTrigger = triggerJob.getTrigger(GhprbTrigger.class)
        def repo = prbTrigger.getGitHub().getRepository(repo)

        repo.createCommitStatus(sha1, status, script.currentBuild.absoluteUrl + 'console', statusText, 'Jenkins CI build')
        script.println "Updating commit status to $status"
    }

    /**
     * Posts a comment on GitHub on the current pull request.
     * @param comment Comment to post.
     */
    @NonCPS
    void postComment(String comment) {
        script.println "Posting comment $comment for pr $prId"
        def triggerJob = script.manager.hudson.getJob(parentJob)
        def prbTrigger = triggerJob.getTrigger(GhprbTrigger.class)
        prbTrigger.getRepository().addComment(Integer.valueOf(prId), comment)
    }

    /**
     * Posts a build summary comment on GitHub on the current pull request.
     * @param buildWrapper Build result wrapper.
     */
    @NonCPS
    void postResultComment(buildWrapper) {
        def commentBuilder = new StringBuilder()
        def buildUrl = Jenkins.activeInstance.rootUrl + buildWrapper.result.rawBuild.url
        commentBuilder.append("Build failed on ${buildWrapper.label}/${buildWrapper.spec}.\n")
        commentBuilder.append("[See console output](${buildUrl}console).\n")
        
        def logParserAction = buildWrapper.result.rawBuild.getAction(LogParserAction.class)
        def testResultAction = buildWrapper.result.rawBuild.getAction(TestResultAction.class)

        def maxMessages = 10

        if (logParserAction?.result?.totalErrors > 0) {
            commentBuilder.append("### Errors:\n")
            def ignoredMessages = 0
            def totalMessages = 0

            logParserAction.result.getErrorLinksReader().withReader {
                def line = null
                while ((line = it.readLine()) != null) {
                    def start = '<span style="color:red">'
                    def startPos = line.indexOf(start) + start.length()
                    def endPos = line.indexOf('</span></a></li><br/>')

                    if (endPos > startPos) {
                        def msg = line.substring(startPos, endPos)
                        
                        if (totalMessages++ < maxMessages) {
                            commentBuilder.append("- $msg \n")                            
                        } else {
                            ignoredMessages++
                        }
                    }
                }
            }

            if (ignoredMessages > 0) {
                commentBuilder.append("\nAnd $ignoredMessages more\n")
            }
            commentBuilder.append("\n")
        }

        if (logParserAction?.result?.totalWarnings > 0) {
            commentBuilder.append("### Warnings:\n")
            def ignoredMessages = 0
            def totalMessages = 0

            logParserAction.result.getWarningLinksReader().withReader {
                def line = null
                while ((line = it.readLine()) != null) {
                    def start = '<span style="color:orange">'
                    def startPos = line.indexOf(start) + start.length()
                    def endPos = line.indexOf('</span></a></li><br/>')

                    if (endPos > startPos) {
                        def msg = line.substring(startPos, endPos)

                        if (totalMessages++ < maxMessages) {
                            commentBuilder.append("- $msg \n")                            
                        } else {
                            ignoredMessages++
                        }
                    }
                }
            }

            if (ignoredMessages > 0) {
                commentBuilder.append("\nAnd $ignoredMessages more\n")
            }

            commentBuilder.append("\n")
        }

        if (testResultAction?.failCount > 0) {
            commentBuilder.append("### Failing tests:\n")
            def ignoredMessages = 0
            def totalMessages = 0

            testResultAction.failedTests.each { test ->
                if (totalMessages++ < maxMessages) {
                    def testLocation = test.getRelativePathFrom(null).minus('junit/')
                    def testUrl = "${buildUrl}testReport/${testLocation}"

                    commentBuilder.append("- [${test.fullName}](${testUrl})\n")
                } else {
                    ignoredMessages++
                }
            }

            if (ignoredMessages > 0) {
                commentBuilder.append("\nAnd $ignoredMessages more\n")
            }
        }

        postComment(commentBuilder.toString())
    }
}
