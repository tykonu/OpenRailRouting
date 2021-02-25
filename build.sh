#! /usr/bin/env bash

set -euo pipefail

MAVEN_OPTS=-Xss20m mvn compile assembly:single -U
