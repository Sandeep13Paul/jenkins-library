def call(Map config) {
    pipeline {
        agent any

        tools {
            maven "${config.mavenTool}"
        }

        environment {
            DOCKERHUB_CREDENTIALS = credentials("${config.dockerCreds}")
            DOCKERHUB_REPO = "${config.dockerRepo}"
            APP_VERSION = "${config.appVersion}"
        }

        stages {
            stage('Test Kubernetes Agent') {
                agent {
                    label 'kubernetes-agent'
                }
                steps {
                    container('jnlp') {
                        sh 'echo "Running in JNLP container"'
                        sh 'java -version'
                    }
                    container('maven') {
                        sh 'echo "Running in Maven container"'
                        sh 'mvn --version'
                    }
                }
            }
            
            stage('Test Docker Agent') {
                agent {
                    label 'docker-agent'
                }
                steps {
                    sh '''
                        echo "🐳 Testing Docker Agent"
                        echo "Agent Info:"
                        echo "==========="
                        hostname
                        whoami
                        pwd
                        echo ""
                        
                        echo "Docker Test:"
                        echo "============"
                        docker --version
                        docker run --rm alpine:latest echo "Container test successful!"
                        
                        echo "✅ All tests passed!"
                    '''
                }
            }

            stage('Checkout from GitHub') {
                steps {
                    checkout scm
                }
            }

            stage('Build with Maven') {
                steps {
                    sh """
                        cd ${config.mavenDir}
                        mvn clean install -DskipTests
                    """
                }
            }

            stage('Build Docker Image') {
                steps {
                    sh """
                        docker build -t $DOCKERHUB_REPO:$APP_VERSION -f Dockerfile .
                    """
                }
            }

            stage('Login to DockerHub') {
                steps {
                    sh """
                        docker login -u $DOCKERHUB_CREDENTIALS_USR -p $DOCKERHUB_CREDENTIALS_PSW
                    """
                }
            }

            stage('Push Docker Image to DockerHub') {
                steps {
                    sh """
                        docker push $DOCKERHUB_REPO:$APP_VERSION
                        docker tag $DOCKERHUB_REPO:$APP_VERSION $DOCKERHUB_REPO:latest
                        docker push $DOCKERHUB_REPO:latest
                    """
                }
            }
        }

        post {
            success {
                echo "Build, Docker image, and push completed successfully!"
            }
            failure {
                echo "Pipeline failed. Check logs for errors."
            }
        }
    }
}
