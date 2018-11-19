#!/bin/sh

echo "Travis + Gradle release"
git config --global user.email "travis@travis-ci.org"
git config --global user.name "Travis CI"
git config --global push.default current
git stash
git remote set-url origin https://$GH_TOKEN@github.com/andreifinski/agent-java-spock
git checkout master
git update-index --chmod=+x gradlew
chmod +x gradlew
./gradlew release -Prelease.useAutomaticVersion=true --debug --stacktrace
