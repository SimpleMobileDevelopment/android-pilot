#!/usr/bin/env zsh
set -e

MARKETING_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$MARKETING_DIR")"

NAMES=(
    "1-dsl-login-route"
    "2-yaml-login-flow"
    "3-yaml-browse-items"
    "4-simple-login-flow"
    "5-simple-browse-items"
)

CLASSES=(
    "co.pilot.sample.PilotDslRouteTest#loginRoute"
    "co.pilot.sample.PilotYamlRouteTest#runLoginFlowFromYaml"
    "co.pilot.sample.PilotYamlRouteTest#runBrowseItemsFromYaml"
    "co.pilot.sample.SamplePilotRouteTest#loginFlow"
    "co.pilot.sample.SamplePilotRouteTest#browseItems"
)

for i in {1..${#NAMES[@]}}; do
    name="${NAMES[$i]}"
    test_class_method="${CLASSES[$i]}"
    video_file="$MARKETING_DIR/${name}.mp4"
    device_path="/sdcard/${name}.mp4"

    echo ""
    echo "========================================="
    echo "Recording: $name"
    echo "Test:      $test_class_method"
    echo "========================================="

    # Start screen recording in background
    adb shell screenrecord --time-limit 120 "$device_path" &
    RECORD_PID=$!
    sleep 1

    # Run the single test
    cd "$PROJECT_DIR"
    ./gradlew :sample:connectedDebugAndroidTest \
        -Pandroid.testInstrumentationRunnerArguments.class="$test_class_method" \
        2>&1 | tail -20

    # Stop recording
    kill $RECORD_PID 2>/dev/null || true
    sleep 2

    # Pull video
    adb pull "$device_path" "$video_file"
    adb shell rm "$device_path"

    echo "Saved: $video_file"
done

echo ""
echo "All recordings complete!"
ls -lh "$MARKETING_DIR"/*.mp4
