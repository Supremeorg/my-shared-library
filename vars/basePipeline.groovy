def call(Map config = [:]) {
    pipeline {
        agent any

        environment {
            // Define defaults only as string literals
            IMAGE = 'brightex99/flaskapps'
        }

        stages {
            stage('Prepare') {
                steps {
                    script {
                        // Dynamically override IMAGE if provided via config
                        if (config.image) {
                            env.IMAGE = config.image
                        }

                        // Now define and expose TAG + IMAGE_TAG
                        env.TAG = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                        env.IMAGE_TAG = "${env.IMAGE}:${env.TAG}"
                        echo "✅ Image to be built: ${env.IMAGE_TAG}"
                    }
                }
            }

            stage('Build') {
                steps {
                    sh "docker build -t $IMAGE_TAG ."
                }
            }

            stage('Snyk Scan') {
                steps {
                    withCredentials([string(credentialsId: 'snyk-token', variable: 'SNYK_TOKEN')]) {
                        sh """
                            echo '🔐 Authenticating Snyk CLI...'
                            snyk auth $SNYK_TOKEN

                            echo '🛡️ Running Snyk scan on Docker image: $IMAGE_TAG'
                            snyk test --docker $IMAGE_TAG --file=Dockerfile
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
                            docker push $IMAGE_TAG
                            docker tag $IMAGE_TAG $IMAGE:latest
                            docker push $IMAGE:latest
                        """
                    }
                }
            }
        }
    }
}
