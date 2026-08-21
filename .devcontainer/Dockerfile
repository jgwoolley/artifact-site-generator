FROM ubuntu:24.04

# Hardened NVM setup (stay as root per user preference)
ARG NVM_VERSION=0.40.7
ENV NVM_DIR=/root/.nvm
ENV NODE_VERSION=24
ENV DEBIAN_FRONTEND=noninteractive

# Install minimal runtime dependencies without recommended extras
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    maven \
    git \
    && rm -rf /var/lib/apt/lists/*

# Install nvm securely and install the desired Node version using nvm
RUN curl -fsSL https://raw.githubusercontent.com/nvm-sh/nvm/v${NVM_VERSION}/install.sh | bash \
    && . "$NVM_DIR/nvm.sh" \
    && nvm install ${NODE_VERSION} \
    && nvm alias default ${NODE_VERSION} \
    && nvm use default \
    # Install Copilot CLI (installed into the nvm-managed node prefix)
    && . "$NVM_DIR/nvm.sh" && npm install -g @github/copilot

# Ensure nvm is loaded for interactive root shells
RUN echo '. "${NVM_DIR}/nvm.sh"' >> /root/.bashrc

WORKDIR /home/ubuntu
