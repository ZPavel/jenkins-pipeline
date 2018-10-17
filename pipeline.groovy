import jenkins.*
import hudson.*

pipeline {
	agent any
	tools {
		maven 'M3'
	}
	environment {
		MANAGER = credentials('tomcat-manager')
	}
	stages {
		stage('SCM Checkout') {
			steps {
				checkout([$class: 'GitSCM', branches: [[name: '*/master']], userRemoteConfigs: [[credentialsId: 'zpavel', url: 'https://github.com/zpavel/myproject.git']]])
			}
		}
		stage('Maven Build') {
			steps {
				sh "mvn clean package -T8"
			}
		}
		stage('Tomcat Deploy') {
			steps {
				script {
					pom = readMavenPom file: 'pom.xml'
					sh "curl \"http://${MANAGER_USR}:${MANAGER_PSW}@myhost.com:8180/manager/text/undeploy?path=/\""
					sh "curl --upload-file mywebapp/target/mywebapp-${pom.version}.war \"http://${MANAGER_USR}:${MANAGER_PSW}@myhost.com:8180/manager/text/deploy?path=/&war=mywebapp\""
				}
			}
		}
		stage('Results') {
			steps {
				archiveArtifacts artifacts: '**/target/*.jar'
				junit '**/target/surefire-reports/TEST-*.xml'
			}
		}
	}
	post {
		success {
			slackSend (color: '#00FF00', message: getChangeSet())
		}
		failure {
			slackSend (color: '#cc0000', message: "FAILED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
		}
	}
}

@NonCPS
def getChangeSet() {
	def aboutJob = "SUCCESSFUL: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})"
    if (currentBuild.changeSets) {
        def changes = currentBuild.changeSets.collect { cs -> cs.collect { entry -> "* ${entry.author.fullName}: ${entry.msg}"}.join("\n")}.join("\n")
        return "${aboutJob}\n${changes}"
    } else {
		return "${aboutJob}\nNo changes since last build"
    }
}
