#!/bin/bash
set -e

mvn package

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar clear-plugins

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar add-plugin ./artifact-site-plugin-maven/target/artifact-site-plugin-maven-*.jar

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar add-plugin ./artifact-site-plugin-vsix/target/artifact-site-plugin-vsix-*.jar

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar add-plugin ./artifact-site-plugin-chrome/target/artifact-site-plugin-chrome-*.jar

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar add-plugin ./artifact-site-plugin-vintagestory/target/artifact-site-plugin-vintagestory-*.jar

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar parse ./**/target/artifact-site-plugin-*.jar

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar parse https://open-vsx.org/api/nf3t/nifi-flowfile-extension/0.0.5/file/nf3t.nifi-flowfile-extension-0.0.5.vsix

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar parse https://github.com/jgwoolley/Canvas-Grades-Extension/archive/refs/tags/v1.3.zip

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar parse https://repo1.maven.org/maven2/org/apache/nifi/nifi-standard-nar/2.11.0/nifi-standard-nar-2.11.0.nar

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar parse https://mods.vintagestory.at/download/129969/nf3ttranslocatorrepair_1.22.7_1.1.0.zip

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar parse https://mods.vintagestory.at/download/86645/nf3tblueprintcopy_1.22.0_1.0.0.zip

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar parse https://mods.vintagestory.at/download/129978/nf3tsponge_1.22.7_2.0.1.zip

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar parse https://mods.vintagestory.at/download/129968/nf3ttranslocatornavigator_1.22.7_2.0.1.zip

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar parse https://mods.vintagestory.at/download/129967/nf3ttranslocatorfindercmd_1.22.7_2.0.1.zip

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar generate