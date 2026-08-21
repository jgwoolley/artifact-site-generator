#!/bin/bash

set -e

docker build -f .devcontainer/Dockerfile -t test .
