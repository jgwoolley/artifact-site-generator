#!/bin/bash
set -e

mvn package

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar add-plugin ./artifact-site-plugin-vsix/target/artifact-site-plugin-vsix-*.jar

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar parse *.vsix