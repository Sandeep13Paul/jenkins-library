def call(Map config) {
    pipeline {
        agent any

        environment {
            DOCKERHUB_REPO = "${config.dockerRepo}"
            APP_VERSION    = "${config.appVersion}"
            GIT_COMMIT     = "${config.gitCommit}"
            K8S_TOKEN      = credentials("${config.k8sCreds}")
            K8S_SERVER     = "${config.k8sServer}"
        }

        stages {
            stage('Checkout from GitHub') {
                steps {
                    checkout scm
                }
            }

            stage('Deploy to Kubernetes') {
                steps {
                    sh """
                        kubectl config set-cluster jenkins-cluster --server=$K8S_SERVER --insecure-skip-tls-verify=true
                        kubectl config set-credentials jenkins --token=$K8S_TOKEN
                        kubectl config set-context my-context --cluster=jenkins-cluster --user=jenkins
                        kubectl config use-context my-context

                        kubectl apply -f deployment.yaml --validate=false
                        kubectl apply -f service.yaml --validate=false

                        kubectl rollout history deployment my-app
                        kubectl set image deployment/my-app my-app-container=$DOCKERHUB_REPO:$APP_VERSION --record
                        kubectl annotate deployment my-app kubernetes.io/change-cause="Deployed via Jenkins build ${BUILD_NUMBER} (commit: ${GIT_COMMIT})" --overwrite
                    """
                }
            }
        }

        post {
            success {
                echo "🚀 Deployment successful!"
            }
            failure {
                echo "❌ Deployment failed. Check logs."
            }
        }
    }
}
