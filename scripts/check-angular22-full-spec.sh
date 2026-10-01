#!/usr/bin/env bash
# Generates the Angular client of openapi/openapi.yaml with the angular22 generator under Artemis's develop options
# (Observable services) and its resource options (httpResource functions), then type-checks each output with the
# generator's pinned TypeScript, Angular and RxJS versions under strict and noUnusedLocals, and prints the error counts.
#
# Usage: scripts/check-angular22-full-spec.sh <openapi-generator-angular22 checkout after ./gradlew build>
set -euo pipefail

generator=$(cd "$1" && pwd)
jar=$(ls "$generator"/build/libs/openapi-generator-angular22-*.jar | grep -v -e sources -e javadoc | head -1)
cli="build/openapi-generator-cli-7.21.0.jar"
[ -f "$cli" ] || curl -fsSL -o "$cli" https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/7.21.0/openapi-generator-cli-7.21.0.jar
sha256sum openapi/openapi.yaml

declare -A options=(
    [develop]="useHttpResource=false,useInjectFunction=true,separateResources=false,readonlyModels=false"
    [resources]="useHttpResource=true,useInjectFunction=true,separateResources=true,readonlyModels=false"
)
for name in develop resources; do
    out="build/angular22-full-spec-check/$name"
    rm -rf "$out"
    mkdir -p "$out"
    java -cp "${cli}:${jar}" org.openapitools.codegen.OpenAPIGenerator generate -g angular22 -i openapi/openapi.yaml -o "$out" \
        --type-mappings set=Array --language-specific-primitives void --additional-properties "${options[$name]}" > "$out.log" 2>&1
    ln -sfn "$generator/src/test/typescript/node_modules" "$out/node_modules"
    printf '{ "extends": "%s", "include": ["api/**/*.ts", "model/**/*.ts"] }\n' "$generator/src/test/typescript/tsconfig.json" > "$out/tsconfig.json"
    echo "== $name: $(find "$out/api" "$out/model" -name '*.ts' | wc -l) files"
    node "$generator/src/test/typescript/node_modules/typescript/bin/tsc" -p "$out/tsconfig.json" > "$out-tsc.log" || true
    grep -o 'error TS[0-9]*' "$out-tsc.log" | sort | uniq -c || echo "      0 errors"
    echo "   in model/: $(grep -c "^$out/model/" "$out-tsc.log" || true), in api/: $(grep -c "^$out/api/" "$out-tsc.log" || true)"
done
