
def call(body) {


    def config = [:]
    def sonarIp = "http://213.32.75.99:9000"
    def deployServerIp = "213.32.75.99"
    def ssh = "root@${deployServerIp}"
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()


    node {
        // Clean workspace before doing anything
        deleteDir()
        try {


            stage ('Clone') {
                checkout scm
            }

            stage ('Build') {
                echo "Compiling and Building..."
                "mvn compile".execute()
            }

            stage ('Tests') {
                parallel 'Test and Build': {
                    echo "Testing..."
                    "mvn test".execute()
                },
                        'Test with Sonar': {
                            echo"Testing With Sonar Server ..."
                            "mvn sonar:sonar -Dsonar.host.url=${sonarIp} -Dsonar.login=4f71b9d7d33b0f4e07d40d3acc7c26dad5e95fdc".execute()

                        }
            }


            stage('Create Docker Image') {
                echo "Cleanning Docker Containers ..."
                def cmd="ssh ${ssh} docker ps -a -q --filter ancestor=${config.projectName} --format='{{.ID}}'  | xargs docker stop 2>/dev/null | xargs docker rm -f "
                try {
                    "${cmd}".execute()
                    echo "Delete Image successfully"
                }catch (Exception exception){
                    echo "No Image to delete"
                }

                echo "Creating Service Image..."
                "mvn package".execute()
            }


            stage('Push Image') {
                echo "Pushing Service Docker Image ..."
                sh "docker save ${config.projectName} | bzip2 | pv | ssh ${ssh} 'bunzip2 | docker load' "
                echo "Stage Test and Build Complete"
            }


            stage('Clean Env') {
                echo "Destroying the build environment..."
                def cmd = "ssh ${ssh}  docker images -f dangling=true -q | xargs docker rmi"
                sh "if [[ ${cmd} -ne 0 ]]; then exit ${cmd}; fi "
                echo "Clean Complete"
            }

            stage('Run Image') {
                echo " Starting Release phase..."
                sh "ssh ${ssh}  docker run -d -p ${config.serverDomain}:${config.serverDomain} ${config.projectName} "
                echo "Realising is Done ! ..."
            }



        } catch (err) {
            currentBuild.result = 'FAILED'
            throw err
        }
    }
}
