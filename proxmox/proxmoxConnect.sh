#!/bin/bash

# Connects to the remote server using SSH

source ./config.env

USER=${1:-$DEFAULT_USER}

SSH_OPTS='-oHostKeyAlgorithms=+ssh-rsa -oPubkeyAcceptedAlgorithms=+ssh-rsa'

echo "User: $USER"


# Establish SSH connection
ssh -p 20127 "$USER@ieticloudpro.ieti.cat"  