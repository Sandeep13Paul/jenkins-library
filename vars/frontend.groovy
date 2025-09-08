def call(Map config) {
    pipeline {
        agent any
    
        environment {
            IMAGE_NAME = "${config.imageName}"
            IMAGE_TAG = "${config.imageTag}"
            DOCKERHUB_CREDENTIALS = credentials("${config.dockerCreds}")
            DOCKER_HUB_REPO = "${config.dockerRepo}"
        }
    
        stages {
            stage('Checkout') {
                steps {
                    echo("Pulling code from GitHub repository")
                    checkout scm
                }
            }
    
            stage('Build Docker Image') {
                steps {
                    script {
                        sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ."
                    }
                }
            }
    
            stage('Login to DockerHub') {
                steps {
                    script {
                        echo " Logging into Docker Hub..."
                        sh """
                            echo $DOCKERHUB_CREDENTIALS_PSW | docker login -u $DOCKERHUB_CREDENTIALS_USR --password-stdin
                        """
                    }
                }
            }
    
            stage('Tag & Push Docker Image') {
                steps {
                    script {
                        echo "Pushing Docker image to DockerHub..."
                        sh """
                            docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${DOCKER_HUB_REPO}:${IMAGE_TAG}
                            docker push ${DOCKER_HUB_REPO}:${IMAGE_TAG}
                            docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${DOCKER_HUB_REPO}:latest
                            docker push ${DOCKER_HUB_REPO}:latest
                        """
                    }
                }
            }
    
        }
    
        post {
            success {
                echo "Pipeline executed successfully!"
            }
            failure {
                echo "Pipeline failed. Please check the logs for details."
            }
        }
    }
}
