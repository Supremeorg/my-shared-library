def call(Map config = [:]) {
    pipeline {
        agent any
        stages {
            stage('Checkout') {
                steps {
                    git url: config.repoUrl
                }
            }
            stage('Build') {
                steps {
                    sh 'docker build -t myimage .'
                }
            }
            stage('Test') {
                steps {
                    sh 'echo "Run tests here"'
                }
            }
        }
    }
}
