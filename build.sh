#! /usr/bin/env bash

set -euo pipefail

mvn compile
mvn compile assembly:single -Dorg.slf4j.simpleLogger.defaultLogLevel=warn
