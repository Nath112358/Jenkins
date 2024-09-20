// Run a docker container using the seleniarm/standalone-firefox:latest image
// Execute automated product demo using maven and cucumber inside the container
// Generate cucumber report with demo run results
pipeline {
    agent any

    environment {
        DOCKER_IMAGE = 'seleniarm/standalone-firefox:latest'
        MAVEN_VERSION = 'maven:3.8.7-openjdk-18'
        MAX_RETRIES = 3 
        CONTAINER_NAME = 'seleniarm-firefox-product-demo'
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', credentialsId: 'ProdGithub', url: 'https://github.com/team/prod.git'
            }
        }

        stage('Run Demo') {
            steps {
                retry(MAX_RETRIES) {
                    script {
                        try {
                            sh """
                            docker run --rm -d --name ${CONTAINER_NAME} -p 4444:4444 ${DOCKER_IMAGE}
                            docker run --rm --network=host -v ${env.WORKSPACE}:/app -w /app ${MAVEN_VERSION} mvn clean test -f Product/pom.xml -Dcucumber.filter.tags=@ProductDemo -Dbrowser="remote-firefox"
                            """
                        } catch (Exception err) {
                            echo "Error: ${err}"
                        } finally {
                            sh """docker stop ${CONTAINER_NAME}"""
                        }
                    }
                }    
            }
            post {
                always {
                    cucumber buildStatus: 'UNSTABLE',
                             fileIncludePattern: '**/*.json',
                             sortingMethod: 'ALPHABETICAL',
                             reportTitle: 'Product Demo Results'
                    sh 'rm -rf target'
                    sh 'rm -rf *-report.txt'
                }
                success {
                    slackSend channel: "#hourly_demo_runs",
                      color: currentBuild.currentResult == 'SUCCESS' ? 'good' : 'danger',
                      message: """*${currentBuild.currentResult}:* <http://jenkins.nath112358.io/job/run_product_demo|Product Demo> (Build #${env.BUILD_NUMBER}) finished in ${currentBuild.durationString.replaceAll(/sec.*/, "sec")}"""
                }
                unstable {
                    slackSend channel: "@Nath112358",
                      color: currentBuild.currentResult == 'SUCCESS' ? 'good' : 'danger',
                      message: """*${currentBuild.currentResult}:* <http://jenkins.nath112358.io/job/run_product_demo|Product Demo> (Build #${env.BUILD_NUMBER}) finished in ${currentBuild.durationString.replaceAll(/sec.*/, "sec")}"""
                }
                failure {
                    slackSend channel: "@Nath112358",
                      color: currentBuild.currentResult == 'SUCCESS' ? 'good' : 'danger',
                      message: """*${currentBuild.currentResult}:* <http://jenkins.nath112358.io/job/run_product_demo|Product Demo> (Build #${env.BUILD_NUMBER}) finished in ${currentBuild.durationString.replaceAll(/sec.*/, "sec")}"""
                }
            }   
        }
    }
}