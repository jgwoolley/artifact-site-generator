#!/bin/bash
set -e

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar info

java -jar ./artifact-site-cli/target/artifact-site-cli-*.jar list-artifacts