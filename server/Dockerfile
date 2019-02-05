FROM openjdk:8-jdk as builder
RUN mkdir /build
COPY ./turnrest/ /build/
WORKDIR /build/
RUN ./gradlew clean build shadowJar
#setup dumb-init
RUN curl -k -L https://github.com/Yelp/dumb-init/releases/download/v1.2.1/dumb-init_1.2.1_amd64 > /tmp/dumb-init

FROM openjdk:10-jre-slim
ARG VERSION
COPY --from=builder /build/build/libs/turnrest-${VERSION}-all.jar /turnrest.jar
COPY --from=builder /tmp/dumb-init /usr/bin/dumb-init
RUN chmod 755 /usr/bin/dumb-init

ADD run.sh /run.sh
RUN chmod 755 /run.sh
RUN touch /env.sh

ENTRYPOINT ["/run.sh"]
CMD ["java","-Xmx128m","-jar","turnrest.jar"]

