#FROM rabbitmq
FROM java:7
MAINTAINER Aleksandr Kartomin kartomin@gmail.com

# ================= Install Maven.
ARG MAVEN_VERSION=3.3.9
ARG USER_HOME_DIR="/root"
RUN mkdir -p /usr/share/maven /usr/share/maven/ref \
  && curl -fsSL http://apache.osuosl.org/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz \
    | tar -xzC /usr/share/maven --strip-components=1 \
  && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn
ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG "$USER_HOME_DIR/.m2"

# ================= Install RabbitMQ.
ENV DEBIAN_FRONTEND=noninteractive
RUN \
  wget -O- https://www.rabbitmq.com/rabbitmq-release-signing-key.asc  | apt-key add - && \
  echo "deb http://www.rabbitmq.com/debian/ testing main" > /etc/apt/sources.list.d/rabbitmq.list && \
  apt-get update && \
  apt-get install -y rabbitmq-server 
RUN \
  rm -rf /var/lib/apt/lists/* && \
  rabbitmq-plugins enable rabbitmq_management 

# ================= Install Chromium.
RUN \
  wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add - && \
  echo "deb http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google.list && \
  apt-get update && \
  apt-get install -y lxde-core lxterminal tightvncserver && \
  apt-get install -y google-chrome-stable && \
  rm -rf /var/lib/apt/lists/*
EXPOSE 5901

# ================= Install chrome
#RUN set -xe \
#    && apt-get update \
#    && apt-get install -y --no-install-recommends xvfb x11vnc fluxbox xterm \
#    && apt-get install -y --no-install-recommends sudo \
#    && apt-get install -y --no-install-recommends supervisor \
#    && rm -rf /var/lib/apt/lists/*
#    
#RUN set -xe \
#    && curl -fsSL https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add - \
#    && echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list \
#    && apt-get update \
#    && apt-get install -y google-chrome-stable \
#    && rm -rf /var/lib/apt/lists/*
#

# ================= Configure root env, paths, scripts
# Add normal user with passwordless sudo
RUN set -xe \
    && useradd -u 1000 -g 100 -G sudo --shell /bin/bash --no-create-home --home-dir /tmp user \
    && echo 'ALL ALL = (ALL:ALL) NOPASSWD: ALL' >> /etc/sudoers 
    
# Tbd by root
WORKDIR /usr/bin/akmakm
RUN chmod 777 ./ /usr/share/maven/ref/ && \
    echo "[{rabbit, [{loopback_users, []}]}]." > /etc/rabbitmq/rabbitmq.config

# ================= Define USER 
USER user

# ================= Download bot.
VOLUME "$USER_HOME_DIR/.m2"
ENV PATH /usr/bin/akmakm/bot:$PATH
RUN git clone https://github.com/akmakm/bot 

# ================= Configure user's env, paths, scripts
WORKDIR /usr/bin/akmakm/bot
RUN chmod +x bot && \
    mv  entry_points/rabbitmq-start /tmp/ && \
    chmod +x /tmp/rabbitmq-start && \
    mv entry_points/mvn-entrypoint.sh /tmp/mvn-entrypoint.sh && \
    chmod +x /tmp/mvn-entrypoint.sh && \
    mv entry_points/settings-docker.xml /usr/share/maven/ref/ 
#ENTRYPOINT ["/tmp/mvn-entrypoint.sh"]
# RabbitMQ
VOLUME ["/data/log", "/data/mnesia", "/tmp"]
ENV RABBITMQ_LOG_BASE /data/log
ENV RABBITMQ_MNESIA_BASE /data/mnesia

# ================= Build bot
RUN mvn package
    
# ================= Configure bash and RabbitMQ to run in the container
CMD ["bash", "--rcfile", "/tmp/rabbitmq-start"]
EXPOSE 15671 15672 5672 36438

#chown: changing ownership of ‘/data/log’: Operation not permitted
#chown: changing ownership of ‘/data/mnesia’: Operation not permitted
#chown: changing ownership of ‘/data’: Operation not permitted
#bash: rabbitmq.log: Permission denied
