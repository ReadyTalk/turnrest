#!/bin/bash

set -e

docker run --rm -u $(id -u ${USER}):$(id -g ${USER}) -v "$PWD":/home/gradle/turnrest -w /home/gradle/turnrest gradle:4.10-jdk8-alpine gradle clean build

VERSION=`cat VERSION`

DP="readytalk/turnrest:"
DPL="${DP}latest"
DPV="${DP}${VERSION}"

docker build --build-arg VERSION=$VERSION -t "${DPL}" .

docker tag ${DPL} ${DPV}

if [[ ${TRAVIS} && "${TRAVIS_BRANCH}" == "master" && -n $DOCKER_USERNAME && -n $DOCKER_PASSWORD ]]; then
  docker login -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD"
  docker push ${DPV}
  docker push ${DPL}
fi
