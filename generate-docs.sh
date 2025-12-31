#!/bin/bash
cd kotlin && ./gradlew dokkaHtml
echo "Documentation generated in docs/dsl/ and docs/solver/"

