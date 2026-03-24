#!/usr/bin/env bash
set -euo pipefail

# Generate pilot.config.json by inspecting the project structure.
# Usage: ./scripts/generate-config.sh [repo-root]

REPO_ROOT="${1:-.}"
cd "$REPO_ROOT"
OUTPUT="pilot.config.json"

echo "Scanning project for Pilot configuration..."
echo ""

# 1. Find the app module (has androidApplication plugin, not "apply false")
APP_GRADLE=$(grep -rl "androidApplication" --include="build.gradle.kts" . \
    | grep -v '/pilot/' \
    | grep -v '/build/' \
    | while read -r f; do
        if ! grep -q "apply false" "$f" 2>/dev/null; then
            echo "$f"
            break
        fi
    done || true)

if [ -z "$APP_GRADLE" ]; then
    echo "Error: Could not find an Android application module."
    echo "Looking for a build.gradle.kts with 'androidApplication' plugin."
    exit 1
fi

APP_MODULE=$(dirname "$APP_GRADLE")
APP_MODULE=${APP_MODULE#./}
MODULE_NAME=$(basename "$APP_MODULE")
echo "  App module: $APP_MODULE"

# 2. Extract applicationId
APP_ID=$(grep -o 'applicationId\s*=\s*"[^"]*"' "$APP_GRADLE" | sed 's/.*"\(.*\)".*/\1/' || true)
if [ -z "$APP_ID" ]; then
    echo "  Warning: Could not extract applicationId, using placeholder"
    APP_ID="com.example.app"
fi
echo "  Package ID: $APP_ID"

# 3. Extract testInstrumentationRunner
TEST_RUNNER=$(grep -o 'testInstrumentationRunner\s*=\s*"[^"]*"' "$APP_GRADLE" | sed 's/.*"\(.*\)".*/\1/' || true)
if [ -z "$TEST_RUNNER" ]; then
    TEST_RUNNER="androidx.test.runner.AndroidJUnitRunner"
    echo "  Test runner: $TEST_RUNNER (default)"
else
    echo "  Test runner: $TEST_RUNNER"
fi

# 4. Find test class (PilotRouteTest or RouteTestRule)
TEST_FILE=$(grep -rl 'PilotRouteTest\|RouteTestRule' --include="*.kt" \
    "$APP_MODULE/src/androidTest/" 2>/dev/null | head -1 || true)

if [ -z "$TEST_FILE" ]; then
    echo "  Warning: No test class found using PilotRouteTest or RouteTestRule"
    echo "  You'll need to create one and update testFile/testClass in the config"
    TEST_FILE="$APP_MODULE/src/androidTest/kotlin/TODO"
    TEST_CLASS="TODO"
else
    TEST_FILE=${TEST_FILE#./}
    PACKAGE=$(grep '^package ' "$TEST_FILE" | awk '{print $2}' | tr -d ';')
    CLASS=$(grep -o 'class [A-Za-z0-9_]*' "$TEST_FILE" | head -1 | awk '{print $2}')
    TEST_CLASS="$PACKAGE.$CLASS"
    echo "  Test class: $TEST_CLASS"
    echo "  Test file:  $TEST_FILE"
fi

# 5. Find YAML routes directory (exclude build dirs)
YAML_DIR=$(find "$APP_MODULE" -path "*/assets/routes" -type d \
    -not -path "*/build/*" 2>/dev/null | head -1 || true)
if [ -z "$YAML_DIR" ]; then
    YAML_DIR="$APP_MODULE/src/main/assets/routes"
    echo "  YAML dir:   $YAML_DIR (will be created)"
else
    YAML_DIR=${YAML_DIR#./}
    YAML_COUNT=$(find "$YAML_DIR" -name "*.yaml" -o -name "*.yml" 2>/dev/null | wc -l | tr -d ' ')
    echo "  YAML dir:   $YAML_DIR ($YAML_COUNT route files)"
fi

# 6. APK paths
DEBUG_APK="$APP_MODULE/build/outputs/apk/debug/$MODULE_NAME-debug.apk"
TEST_APK="$APP_MODULE/build/outputs/apk/androidTest/debug/$MODULE_NAME-debug-androidTest.apk"
echo "  Debug APK:  $DEBUG_APK"
echo "  Test APK:   $TEST_APK"

# 7. Write config
echo ""
cat > "$OUTPUT" <<EOJSON
{
  "appPackageId": "$APP_ID",
  "testRunner": "$TEST_RUNNER",
  "testClass": "$TEST_CLASS",
  "yamlDir": "$YAML_DIR",
  "testFile": "$TEST_FILE",
  "testFileTemplate": "$TEST_FILE",
  "debugApk": "$DEBUG_APK",
  "testApk": "$TEST_APK",
  "buildCommand": ["./gradlew", ":$MODULE_NAME:assembleDebug", ":$MODULE_NAME:assembleDebugAndroidTest"],
  "github": {
    "owner": "",
    "repo": "",
    "workflowFile": "route-tests.yml"
  }
}
EOJSON

echo "Generated $OUTPUT"
echo "Review the values above and edit $OUTPUT if needed."
