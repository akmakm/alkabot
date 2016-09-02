#FROM rabbitmq
FROM java:7
MAINTAINER Aleksandr Kartomin kartomin@gmail.com

# ================= Download bot.
WORKDIR /usr/bin/akmakm
RUN git clone  https://github.com/akmakm/bot 

# ================= Install Maven.
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
    chmod +x /usr/local/bin/mvn-entrypoint.sh && \
    mv bot/entry_points/settings-docker.xml /usr/share/maven/ref/ 

# Build bot
WORKDIR /usr/bin/akmakm/bot
RUN mvn package
    
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

# Define environment variables.
ENV RABBITMQ_LOG_BASE /data/log
ENV RABBITMQ_MNESIA_BASE /data/mnesia
ENV PATH /usr/bin/akmakm/bot:$PATH
    
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
RUN set -xe \
    && apt-get update \
    && apt-get install -y --no-install-recommends xvfb x11vnc fluxbox xterm \
    && apt-get install -y --no-install-recommends sudo \
    && apt-get install -y --no-install-recommends supervisor \
    && rm -rf /var/lib/apt/lists/*
    
#RUN set -xe \
#    && curl -fsSL https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add - \
#    && echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list \
#    && apt-get update \
#    && apt-get install -y google-chrome-stable \
#    && rm -rf /var/lib/apt/lists/*
#
# RabbitMQ
VOLUME ["/data/log", "/data/mnesia", "/tmp"]
# Add normal user with passwordless sudo
RUN set -xe \
    && useradd -u 1000 -g 100 -G sudo --shell /bin/bash --no-create-home --home-dir /tmp user \
    && echo 'ALL ALL = (ALL:ALL) NOPASSWD: ALL' >> /etc/sudoers \
    && chmod 755 /root/ \
    && chmod 777 /root/.m2 \ 
    && touch /root/.m2/copy_reference_file.log \
    && chmod 777 /root/.m2/copy_reference_file.log \
    && chown user /data/ \
    && chown user /data/log \
    && chown user /data/mnesia

#COPY supervisord.conf /etc/
#COPY chrome_entry.sh /
#
#WORKDIR /tmp
#VOLUME /tmp/chrome-data
#ENTRYPOINT ["/chrome_entry.sh"]

# Tbd by root
WORKDIR /usr/bin/akmakm
RUN chmod +x bot/bot && \
    mv bot/entry_points/rabbitmq-start /usr/local/bin/ && \
    echo "[{rabbit, [{loopback_users, []}]}]." > /etc/rabbitmq/rabbitmq.config && \
    chmod +x /usr/local/bin/rabbitmq-start

# Define USER and CMD
USER user
#chown: changing ownership of ‘/data/log’: Operation not permitted
#chown: changing ownership of ‘/data/mnesia’: Operation not permitted
#chown: changing ownership of ‘/data’: Operation not permitted
#bash: rabbitmq.log: Permission denied

VOLUME "$USER_HOME_DIR/.m2"
ENTRYPOINT ["/usr/local/bin/mvn-entrypoint.sh"]

WORKDIR /usr/bin/akmakm/bot
CMD ["bash", "--rcfile", "/usr/local/bin/rabbitmq-start"]

EXPOSE 15671 15672 5672 36438

