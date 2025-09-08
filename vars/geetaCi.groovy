def call(Map config) {
    pipeline {
        agent any
        
        environment {
            DOCKERHUB_CREDENTIALS = credentials("${config.dockerCreds}")
            DOCKERHUB_REPO = "${config.dockerRepo}"
            APP_VERSION = "${config.appVersion}"
            DOCKER_CMD = "/usr/local/bin/docker"
            KUBECTL_CMD = "/usr/local/bin/kubectl"
        }
        
        stages {
            stage('Checkout from GitHub') {
                steps {
                    echo "Cloning GitHub repository"
                    checkout scm
                }
            }
            
            stage('Build Docker Image') {
                steps {
                    script {
                        echo "Building Docker image from Dockerfile"
                        sh """
                            export DOCKER_CONFIG=\$(mktemp -d)
                            echo '{}' > \$DOCKER_CONFIG/config.json
                            docker build -t $DOCKERHUB_REPO:$APP_VERSION .
                        """
                    }
                }
            }
            
            stage('Login to DockerHub') {
                steps {
                    script {
                        echo "Logging into Docker Hub"
                        sh """
                            docker login -u $DOCKERHUB_CREDENTIALS_USR -p $DOCKERHUB_CREDENTIALS_PSW
                        """
                    }
                }
            }
            
            stage('Push Docker Image to DockerHub') {
                steps {
                    script {
                        echo "Pushing Docker image to DockerHub"
                        sh """
                            docker push $DOCKERHUB_REPO:$APP_VERSION
                            docker tag $DOCKERHUB_REPO:$APP_VERSION $DOCKERHUB_REPO:latest
                            docker push $DOCKERHUB_REPO:latest
                        """
                    }
                }
            }
        }
        
        post {
            success {
                echo "Application build and pushed to docker sucessfully with image: $DOCKERHUB_REPO:$APP_VERSION"
            }
            failure {
                echo "Pipeline failed"
            }
            always {
                echo "Cleaning up Docker images"
                sh '''
                    docker image prune -f || true
                    docker container prune -f || true
                '''
            }
        }
    }
}