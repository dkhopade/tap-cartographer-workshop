#!/usr/bin/env bash
set -ex

#IMAGE_REPO=harbor.services.demo.jg-aws.com
IMAGE_REPO=dktapdemo.azurecr.io

# echo "Reinitialize password manager"
# pass show docker-credential-helpers/docker-pass-initialized-check

# echo "Docker login"
docker login $IMAGE_REPO --username dktapdemo --password 'QMOAadagDR9/u/kLXt/pLWxFbSrOJRbZlnXE+YxU4U+ACRB9CKBD'

echo "Building workshop image"
docker build . -t $IMAGE_REPO/tap-workshop/cartographer-workshop

echo "Pushing the workshop image"
docker push $IMAGE_REPO/tap-workshop/cartographer-workshop:latest
