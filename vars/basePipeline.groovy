def call(Map config = [:]) {
    pipeline {
        agent any

        environment {
            IMAGE = 'brightex99/flaskapps'
        }

        stages {
            stage('Prepare') {
                steps {
                    script {
                        if (config.image) {
                            env.IMAGE = config.image
                        }

                        env.TAG = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                        env.IMAGE_TAG = "${env.IMAGE}:${env.TAG}"
                        echo "✅ Image to be built: ${env.IMAGE_TAG}"
                    }
                }
            }

            stage('Build') {
                steps {
                    sh "docker build -t ${env.IMAGE_TAG} ."
                }
            }

            stage('Snyk Scan') {
                steps {
                    withCredentials([string(credentialsId: 'snyk-token', variable: 'SNYK_TOKEN')]) {
                        sh """
                            echo '🔐 Authenticating Snyk CLI...'
                            snyk auth ${SNYK_TOKEN}

                            echo '🛡️ Running Snyk scan on Docker image: ${env.IMAGE_TAG}'
                            snyk test --docker ${env.IMAGE_TAG} --file=Dockerfile
                        """
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
                            docker push ${env.IMAGE_TAG}
                            docker tag ${env.IMAGE_TAG} ${env.IMAGE}:latest
                            docker push ${env.IMAGE}:latest
                        """
                    }
                }
            }
        }
    }
}
