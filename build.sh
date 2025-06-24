#!/bin/bash

# Exit on any error
set -e

# Verify indicators.json is in resources/
echo "Verifying indicators.json in resources/..."
if [ -f resources/indicators.json ]; then
  echo "Found indicators.json in resources/."
elif [ -f indicators.json ]; then
  mkdir -p resources
  cp indicators.json resources/indicators.json
  echo "Copied indicators.json to resources/indicators.json"
else
  echo "Error: indicators.json not found in project root or resources/. Please place it in resources/."
  exit 1
fi

# Verify test-indicators.json in test/resources/
echo "Verifying test-indicators.json in test/resources/..."
if [ -f test/resources/test-indicators.json ]; then
  echo "Found test-indicators.json in test/resources/."
elif [ -f test-indicators.json ]; then
  mkdir -p test/resources
  cp test-indicators.json test/resources/test-indicators.json
  echo "Copied test-indicators.json to test/resources/test-indicators.json"
else
  echo "Error: test-indicators.json not found in project root or test/resources/. Please place it in test/resources/."
  exit 1
fi

# Build the Uberjar
echo "Building Uberjar 0.0.7..."
lein clean
lein uberjar

# Debug: List target directory contents
echo "Contents of target/ directory:"
ls -l target/

# Ensure the target/uberjar directory exists
mkdir -p target/uberjar

# Check for standalone JAR (per :uberjar-name in project.clj)
if [ -f target/vv-otx-service-0.0.7-standalone.jar ]; then
  cp target/vv-otx-service-0.0.7-standalone.jar target/uberjar/vv-otx-service-0.0.7-standalone.jar
  echo "Copied target/vv-otx-service-0.0.7-standalone.jar to target/uberjar/vv-otx-service-0.0.7-standalone.jar"
elif [ -f target/vv-otx-service-0.0.7.jar ]; then
  cp target/vv-otx-service-0.0.7.jar target/uberjar/vv-otx-service-0.0.7.jar
  echo "Copied target/vv-otx-service-0.0.7.jar to target/uberjar/vv-otx-service-0.0.7.jar"
else
  echo "Error: No JAR found in target/. Searching for any JAR..."
  find target/ -name "*.jar"
  exit 1
fi

# Verify JAR in target/uberjar/
if [ -f target/uberjar/vv-otx-service-0.0.7-standalone.jar ]; then
  echo "Verified JAR at target/uberjar/vv-otx-service-0.0.7-standalone.jar"
elif [ -f target/uberjar/vv-otx-service-0.0.7.jar ]; then
  echo "Verified JAR at target/uberjar/vv-otx-service-0.0.7.jar"
else
  echo "Error: No JAR found in target/uberjar/. Build failed."
  exit 1
fi

echo "Build complete. Uberjar is located in target/uberjar/"
echo "Ready to build Docker image with: docker build -t vv-otx-service ."