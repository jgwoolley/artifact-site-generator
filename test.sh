#!/bin/bash
set -e

mvn package

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar clear-plugins

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar add-plugin ./artifact-site-plugin-vsix/target/artifact-site-plugin-vsix-*.jar

if [[ ! -f nf3t.nifi-flowfile-extension-0.0.5.vsix ]];then
    wget https://open-vsx.org/api/nf3t/nifi-flowfile-extension/0.0.5/file/nf3t.nifi-flowfile-extension-0.0.5.vsix -O nf3t.nifi-flowfile-extension-0.0.5.vsix
fi

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar parse https://open-vsx.org/api/nf3t/nifi-flowfile-extension/0.0.5/file/nf3t.nifi-flowfile-extension-0.0.5.vsix

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar generate

python3 -m http.server -d ./public/