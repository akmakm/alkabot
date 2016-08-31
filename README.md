bot
============
#Bot. It resizes given images and saves them to remote
cloud storage.

# Platforms
Bot should work under Linux (Ubuntu, Debian, Centos) system.

# Installation
Use the dockerfile:
  docker build -f Dockerfile -t bot:v1 ./

# Testing the Installation
Run the container built on installation step:
  docker run -it --rm --name alkabot -p 15672:15672 -p 5672:5672 -p 25672:25672 bot:v1
Check with your browser that RabbitMQ server is running 
(via its management interface at http://localhost:15672/#/queues)
Verify all commands execution by bot. Note that first operation may take longer time
for initialization.
 - bot schedule <images dir>
 - bot resize [-n N]
 - bot upload [-n N]
 - bot retry [-n N]
 - bot status
 
# Configuration
If used with other than default RabbitMQ, specify its connection 
settings in alkabot.json as follows:
   * "RABBITMQ_HOST" : "localhost"
   * "RABBITMQ_PORT" : "5672"
   * "RABBITMQ_USERNAME" : "guest"
   * "RABBITMQ_PASSWORD" : "guest"
   * "RABBITMQ_VIRTUAL_HOST" : "/"
If needed to use other than default temporary folder for storing
cloud credentials, specify it in alkabot.json as follows:  
   * "TMP_FOLDER" : "/tmp"



