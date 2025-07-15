def call(Map config = [:]) {
    pipeline {
        agent any

        stages {
            stage('Prepare') {
                steps {
                    script {
                        IMAGE = config.image ?: 'brightex99/flaskapps'
                        TAG = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                        IMAGE_TAG = "${IMAGE}:${TAG}"
                        echo "Image to be built: ${IMAGE_TAG}"
                    }
                }
            }

            stage('Build') {
                steps {
                    sh "docker build -t ${IMAGE_TAG} ."
                }
            }

            stage('Snyk Scan') {
                steps {
                    withCredentials([string(credentialsId: 'snyk-token', variable: 'SNYK_TOKEN')]) {
                        sh '''
                            curl -sL https://snyk.io/install | bash
                            export PATH=$PATH:/root/.snyk
                            snyk auth $SNYK_TOKEN
                            snyk test --docker ${IMAGE_TAG} --file=Dockerfile
                        '''
                    }
                }
            }

            stage('Push to Docker Hub') {
                steps {
                    withCredentials([usernamePassword(
                        credentialsId: 'docker-creds',
                        usernameVariable: 'USER',
                        passwordVariable: 'PASS'
                    )]) {
                        sh """
                            echo "$PASS" | docker login -u "$USER" --password-stdin
                            docker push ${IMAGE_TAG}
                            docker tag ${IMAGE_TAG} ${IMAGE}:latest
                            docker push ${IMAGE}:latest
                        """
                    }
                }
            }
        }
    }
}
