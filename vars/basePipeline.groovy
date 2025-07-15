def call(Map config = [:]) {
    pipeline {
        agent any
        environment {
            IMAGE = config.image ?: 'brightex99/flaskapps'
            DOCKER_CREDENTIALS = 'docker-creds'
            SNYK_TOKEN_ID = 'snyk-token'
        }

        stages {
            stage('Set Tag') {
                steps {
                    script {
                        TAG = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                        IMAGE_TAG = "${IMAGE}:${TAG}"
                        echo "Tag: ${IMAGE_TAG}"
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
                    withCredentials([string(credentialsId: SNYK_TOKEN_ID, variable: 'SNYK_TOKEN')]) {
                        sh '''
                            curl -sL https://snyk.io/install | bash
                            export PATH=$PATH:/root/.snyk
                            snyk auth $SNYK_TOKEN
                            snyk test --docker ${IMAGE_TAG} --file=Dockerfile
                        '''
                    }
                }
            }

            stage('Push to DockerHub') {
                steps {
                    withCredentials([usernamePassword(
                        credentialsId: DOCKER_CREDENTIALS,
                        usernameVariable: 'USER',
                        passwordVariable: 'PASS'
                    )]) {
                        sh '''
                            echo "$PASS" | docker login -u "$USER" --password-stdin
                            docker push ${IMAGE_TAG}
                            docker tag ${IMAGE_TAG} ${IMAGE}:latest
                            docker push ${IMAGE}:latest
                        '''
                    }
                }
            }
        }
    }
}
