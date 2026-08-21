#!/bin/bash

set -e

VOLUME_NAME=artifact_copilot

# create named volume if it doesn't exist
docker volume inspect "$VOLUME_NAME" >/dev/null 2>&1 || docker volume create "$VOLUME_NAME"

docker run -it \
  -v "$PWD:/home/ubuntu" \
  -v "$VOLUME_NAME:/root/.copilot" \
  -w /home/ubuntu \
  test /bin/bash
