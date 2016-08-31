#FROM rabbitmq
FROM java:7
MAINTAINER Aleksandr Kartomin kartomin@gmail.com

# Download bot 
WORKDIR /usr/bin/akmakm
RUN apt-get install -y git && \
    git clone  https://github.com/akmakm/bot 
RUN chmod +x bot/bot && \
    mv bot/entry_points/rabbitmq-start /usr/local/bin/

# Install Maven
ARG MAVEN_VERSION=3.3.9
ARG USER_HOME_DIR="/root"
RUN mkdir -p /usr/share/maven /usr/share/maven/ref \
  && curl -fsSL http://apache.osuosl.org/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz \
    | tar -xzC /usr/share/maven --strip-components=1 \
  && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn
ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG "$USER_HOME_DIR/.m2"
RUN \
    mv bot/entry_points/mvn-entrypoint.sh /usr/local/bin/mvn-entrypoint.sh && \
    mv bot/entry_points/settings-docker.xml /usr/share/maven/ref/
VOLUME "$USER_HOME_DIR/.m2"
ENTRYPOINT ["/usr/local/bin/mvn-entrypoint.sh"]

# Build bot
WORKDIR /usr/bin/akmakm/bot
RUN mvn clean package
    
# Install RabbitMQ.
RUN \
  wget -O- https://www.rabbitmq.com/rabbitmq-release-signing-key.asc  | apt-key add - && \
  echo "deb http://www.rabbitmq.com/debian/ testing main" > /etc/apt/sources.list.d/rabbitmq.list && \
  apt-get update && \
  DEBIAN_FRONTEND=noninteractive apt-get install -y rabbitmq-server 
RUN \
  rm -rf /var/lib/apt/lists/* && \
  rabbitmq-plugins enable rabbitmq_management && \
  echo "[{rabbit, [{loopback_users, []}]}]." > /etc/rabbitmq/rabbitmq.config && \
  chmod +x /usr/local/bin/rabbitmq-start

# Define environment variables.
ENV RABBITMQ_LOG_BASE /data/log
ENV RABBITMQ_MNESIA_BASE /data/mnesia
ENV PATH /usr/bin/akmakm/bot:$PATH
    
# Define mount points.
VOLUME ["/data/log", "/data/mnesia", "/tmp"]

# Define CMD
CMD ["bash", "--rcfile", "/usr/local/bin/rabbitmq-start"]

EXPOSE 15671 15672 5672 25672

