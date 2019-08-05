FROM openjdk:10-jre-slim
ARG VERSION
COPY build/libs/turnrest-${VERSION}-all.jar /turnrest-${VERSION}.jar
RUN apt-get update && apt-get install -y dumb-init && rm -rf /var/lib/apt/lists/*

ADD run.sh /run.sh
RUN chmod 755 /run.sh
RUN touch /env.sh

ENV VERSION ${VERSION}

ENTRYPOINT ["/usr/bin/dumb-init", "--"]
CMD ["/run.sh", "java -Xmx128m -jar turnrest-${VERSION}.jar"]

