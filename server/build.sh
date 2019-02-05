#!/bin/bash
set -e


VERSION=`cat turnrest/VERSION`
SV="turnrest-${VERSION}"

echo $VERSION

docker build --build-arg VERSION=$VERSION -t turnrest:latest .

docker tag turnrest:latest readytalk/turnrest:${VERSION}
docker tag turnrest:latest readytalk/turnrest:latest

echo "Created readytalk/turnrest:${VERSION}"

if [[ ${TRAVIS} && "${TRAVIS_BRANCH}" == "master" && -n $DOCKER_USERNAME && -n $DOCKER_PASSWORD ]]; then
  docker login -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD"
  docker push readytalk/turnrest:${VERSION}
  docker push readytalk/turnrest:latest
fi

