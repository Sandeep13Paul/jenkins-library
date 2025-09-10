def call(Map config) {
    pipeline {
        agent any

        environment {
            DOCKERHUB_REPO = "${config.dockerRepo}"
            APP_VERSION    = "${config.appVersion}"
            GIT_COMMIT     = "${config.gitCommit}"
            K8S_TOKEN      = credentials("${config.k8sCreds}")
            K8S_SERVER     = "${config.k8sServer}"
            PATH = "/usr/local/bin:${env.PATH}"
        }

        stages {
            stage('Checkout from GitHub') {
                steps {
                    checkout scm
                }
            }
            stage('Install Helm') {
                steps {
                    sh """
                        curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
                    """
                }
            }


            stage('Deploy with Helm') {
                steps {
                    sh """
                        kubectl config set-cluster jenkins-cluster --server=$K8S_SERVER --insecure-skip-tls-verify=true
                        kubectl config set-credentials jenkins --token=$K8S_TOKEN
                        kubectl config set-context my-context --cluster=jenkins-cluster --user=jenkins
                        kubectl config use-context my-context

                        helm upgrade --install my-app ./my-app-chart \
                          --set image.repository=$DOCKERHUB_REPO \
                          --set image.tag=$APP_VERSION \
                          --set ingress.hosts[0].host=my-app.example.com

                        kubectl set image deployment/my-app-my-app my-app-container=${DOCKERHUB_REPO}:${APP_VERSION} --record

                        kubectl annotate deployment my-app-my-app kubernetes.io/change-cause="Deployed version ${APP_VERSION} (commit: ${GIT_COMMIT})" --overwrite

                        kubectl rollout history deployment my-app-my-app

                    """
                }
            }
        }

        post {
            success {
                echo "Deployment successful with Helm + Ingress!"
            }
            failure {
                echo "Deployment failed. Check logs."
            }
        }
    }
}
