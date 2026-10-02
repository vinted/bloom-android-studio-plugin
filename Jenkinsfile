pipeline {
    agent {
        label 'android-build'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    stages {
        stage('verify') {
            steps {
                sh './gradlew clean test buildPlugin'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'build/test-results/**/*.xml'
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'build/distributions/*.zip'
                }
            }
        }

        stage('publish to JetBrains Marketplace') {
            when {
                expression {
                    env.TAG_NAME?.startsWith('bloom-plugin-v')
                }
            }
            options {
                retry(3)
            }
            steps {
                script {
                    def expectedVersion = env.TAG_NAME.substring('bloom-plugin-v'.length())
                    def pluginVersion = sh(
                        script: "./gradlew -q properties | awk '/^version:/{print \$2}'",
                        returnStdout: true,
                    ).trim()
                    if (pluginVersion != expectedVersion) {
                        error("Plugin version ${pluginVersion} does not match release tag ${expectedVersion}")
                    }
                }
                withCredentials([
                    string(credentialsId: 'jetbrains-marketplace-publish-token', variable: 'PUBLISH_TOKEN'),
                    string(credentialsId: 'jetbrains-marketplace-certificate-chain', variable: 'CERTIFICATE_CHAIN'),
                    string(credentialsId: 'jetbrains-marketplace-private-key', variable: 'PRIVATE_KEY'),
                    string(credentialsId: 'jetbrains-marketplace-private-key-password', variable: 'PRIVATE_KEY_PASSWORD'),
                ]) {
                    sh './gradlew publishPlugin'
                }
            }
        }
    }
}
