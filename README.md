# bot
Bot. It resizes given images and saves them to remote
cloud storage.

# Platforms
Bot should work under Linux (Ubuntu, Debian, Centos) system.

# Installation
Use dockerfile - tbd 

# Testing the Installation
tbd

# Configuration
If used with other than default RabbitMQ, specify its connection 
settings in alkabot.json as follows:
    "RABBITMQ_HOST" : "rabbit",
    "RABBITMQ_PORT" : "5672",
    "RABBITMQ_USERNAME" : "guest",
    "RABBITMQ_PASSWORD" : "guest",
    "RABBITMQ_VIRTUAL_HOST" : "/",
If needed to use other than default temporary folder for storing
cloud credentials, specify it in alkabot.json as follows:  
    "TMP_FOLDER" : "/tmp"



