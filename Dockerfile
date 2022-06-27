ARG OPENJDK_TAG=11.0.15

FROM openjdk:${OPENJDK_TAG}

ARG SBT_VERSION=1.6.2

# prevent this error: java.lang.IllegalStateException: cannot run sbt from root directory without -Dsbt.rootdir=true; see sbt/sbt#1458
WORKDIR /muse

# Install sbt
RUN \
  mkdir /working/ && \
  cd /working/ && \
  curl -L -o sbt-$SBT_VERSION.deb https://repo.scala-sbt.org/scalasbt/debian/sbt-$SBT_VERSION.deb && \
  dpkg -i sbt-$SBT_VERSION.deb && \
  rm sbt-$SBT_VERSION.deb && \
  apt-get update && \
  apt-get install sbt && \
  cd && \
  rm -r /working/ && \
  sbt sbtVersion

WORKDIR /muse
COPY src src
COPY project/build.properties project/build.properties
COPY project/ConsoleHelper.scala project/ConsoleHelper.scala
COPY project/plugins.sbt project/plugins.sbt
COPY build.sbt build.sbt
RUN sbt assembly

EXPOSE 8883
CMD java -Xms2G -Xmx2G -server -jar /muse/target/scala-3.1.0/muse.jar


