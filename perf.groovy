// Groovy Script: http://www.groovy-lang.org/syntax.html
// Jenkins DSL: https://github.com/jenkinsci/job-dsl-plugin/wiki

import jobs.generation.*;

// The input project name (e.g. dotnet/corefx)
def projectName = GithubProject
// The input branch name (e.g. master)
def branchName = GithubBranchName
def defaultBranch = "*/${branchName}"

def isPr = false;

def jobName = Utilities.getFullJobName(projectName, "perf_run", isPr)
def myJob = job(jobName) {
    description('perf run')

    steps {
        powerShell("""
            Invoke-WebRequest -Uri http://dotnetci.blob.core.windows.net/roslyn/cpc.zip -OutFile cpc.zip
            [Reflection.Assembly]::LoadWithPartialName('System.IO.Compression.FileSystem') | Out-Null
            Remove-Item -Recurse -Force /CPC
            [IO.Compression.ZipFile]::ExtractToDirectory('cpc.zip', '/CPC/')
            """)
        batchFile("""
            restore.cmd
            msbuild Roslyn /p:configuration=Release
            Binaries\\Release\\Roslyn.Test.Performance.Runner.exe --no-trace-upload
            """)
    }

    publishers {
        postBuildScripts {
            steps {
                powerShell("""
                    # If the test runner crashes and doesn't shut down CPC, CPC could fill
                    # the entire disk with ETL traces.
                    taskkill /f /fi CPC.exe

                    # Move all etl files to the a folder for archiving
                    mkdir ToArchive
                    mv /CPC/DataBackup* ToArchive

                    # Clean CPC out of the machine
                    Remove-Item -Recurse -Force /CPC
                    Remove-Item -Recurse -Force /CPCTraces
                    Remove-Item -Recurse -Force /PerfLogs
                    Remove-Item -Recurse -Force /PerfTemp""")
            }
        }
    }
}

Utilities.addArchival(myJob, "ToArchive")
Utilities.standardJobSetup(myJob, projectName, isPr, defaultBranch)
Utilities.setMachineAffinity(myJob, 'Windows_NT', 'latest-or-auto-elevated')
Utilities.addGithubPushTrigger(myJob)
