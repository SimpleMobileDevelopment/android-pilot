#!/usr/bin/env python3
"""
Custom HTTP server for the Route Builder tool.

Serves static files from its own directory and exposes API endpoints
for local test execution on connected ADB devices.

Usage:
    python3 server.py --port 8080 --config /path/to/pilot.config.json --repo-root /path/to/project
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import threading
import uuid
from functools import partial
from http.server import HTTPServer, SimpleHTTPRequestHandler


# Global state for test runs
runs = {}
runs_lock = threading.Lock()
current_run_id = None
current_run_lock = threading.Lock()

# Config loaded at startup
config = {}


def check_device():
    """Check if an ADB device is connected."""
    try:
        result = subprocess.run(
            ["adb", "devices"],
            capture_output=True,
            text=True,
            timeout=10,
        )
        lines = result.stdout.strip().split("\n")
        # First line is "List of devices attached", remaining lines are devices
        devices = [
            line for line in lines[1:] if line.strip() and "\tdevice" in line
        ]
        if devices:
            device_name = devices[0].split("\t")[0]
            return True, device_name
        return False, ""
    except Exception:
        return False, ""


def run_test_background(run_id, yaml_content, filename, test_method_code, method_name, repo_root):
    """Execute a test run in a background thread."""
    global current_run_id

    original_test_content = None
    yaml_path = os.path.join(repo_root, config["yamlDir"], filename)
    test_file_path = os.path.join(repo_root, config["testFile"])
    scaffolded = False

    def update_run(phase=None, status=None, append_output=None):
        with runs_lock:
            if phase is not None:
                runs[run_id]["phase"] = phase
            if status is not None:
                runs[run_id]["status"] = status
            if append_output is not None:
                runs[run_id]["output"] += append_output

    def cleanup():
        """Remove YAML file and restore test file."""
        try:
            if os.path.exists(yaml_path):
                os.remove(yaml_path)
        except Exception:
            pass
        try:
            if scaffolded:
                # Remove the scaffolded file entirely
                if os.path.exists(test_file_path):
                    os.remove(test_file_path)
            elif original_test_content is not None:
                with open(test_file_path, "w") as f:
                    f.write(original_test_content)
        except Exception:
            pass
        with current_run_lock:
            global current_run_id
            current_run_id = None

    def run_subprocess(cmd, phase_name, cwd=None):
        """Run a subprocess, streaming output line by line. Returns exit code."""
        update_run(phase=phase_name)
        update_run(append_output=f"\n=== {phase_name.upper()} ===\n")
        try:
            proc = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                cwd=cwd or repo_root,
                text=True,
            )
            for line in proc.stdout:
                update_run(append_output=line)
            proc.wait()
            return proc.returncode
        except Exception as e:
            update_run(append_output=f"Error running command: {e}\n")
            return -1

    try:
        # Phase: writing
        update_run(phase="writing")
        update_run(append_output="=== WRITING ===\n")

        # Write YAML file
        os.makedirs(os.path.dirname(yaml_path), exist_ok=True)
        with open(yaml_path, "w") as f:
            f.write(yaml_content)
        update_run(append_output=f"Wrote YAML to {yaml_path}\n")

        # Scaffold test file from template if it doesn't exist
        if not os.path.exists(test_file_path):
            template_rel = config.get("testFileTemplate", "")
            template_path = os.path.join(repo_root, template_rel) if template_rel else ""
            if not template_path or not os.path.exists(template_path):
                update_run(
                    status="error",
                    phase="done",
                    append_output=f"Error: test file not found at {test_file_path} "
                                  f"and no valid testFileTemplate configured\n",
                )
                cleanup()
                return
            os.makedirs(os.path.dirname(test_file_path), exist_ok=True)
            shutil.copy2(template_path, test_file_path)
            scaffolded = True
            update_run(append_output=f"Created test file from template\n")

        # Read and modify test file
        with open(test_file_path, "r") as f:
            original_test_content = f.read()

        # Find the last closing brace at start of line (class closing brace)
        matches = list(re.finditer(r"^}", original_test_content, re.MULTILINE))
        if not matches:
            update_run(
                status="error",
                phase="done",
                append_output=f"Error: could not find class closing brace in {test_file_path}\n",
            )
            cleanup()
            return

        insert_pos = matches[-1].start()
        modified_content = (
            original_test_content[:insert_pos]
            + test_method_code + "\n"
            + original_test_content[insert_pos:]
        )
        with open(test_file_path, "w") as f:
            f.write(modified_content)
        update_run(append_output="Modified test file with new test method\n")

        # Phase: building
        api_key = os.environ.get("ROUTES_AI_API_KEY", "") or os.environ.get("PILOT_API_KEY", "")
        build_cmd = list(config["buildCommand"]) + [
            f"-PROUTES_AI_API_KEY={api_key}",
            "--no-daemon",
        ]
        exit_code = run_subprocess(build_cmd, "building")
        if exit_code != 0:
            update_run(status="error", phase="done", append_output=f"\nBuild failed with exit code {exit_code}\n")
            cleanup()
            return

        # Phase: installing
        exit_code = run_subprocess(
            ["adb", "install", "-r", os.path.join(repo_root, config["debugApk"])],
            "installing",
        )
        if exit_code != 0:
            update_run(status="error", phase="done", append_output=f"\nInstall debug APK failed with exit code {exit_code}\n")
            cleanup()
            return

        exit_code = run_subprocess(
            ["adb", "install", "-r", os.path.join(repo_root, config["testApk"])],
            "installing",
        )
        if exit_code != 0:
            update_run(status="error", phase="done", append_output=f"\nInstall test APK failed with exit code {exit_code}\n")
            cleanup()
            return

        # Clear app data for fresh state (mirrors CI/Firebase behavior)
        run_subprocess(
            ["adb", "shell", "pm", "clear", config["appPackageId"]],
            "running",
        )

        # Phase: running
        exit_code = run_subprocess(
            [
                "adb", "shell", "am", "instrument", "-w",
                "-e", "class", f"{config['testClass']}#{method_name}",
                f"{config['appPackageId']}.test/{config['testRunner']}",
            ],
            "running",
        )

        # Check output for actual test result (exit code is unreliable)
        with runs_lock:
            output = runs[run_id]["output"]
        if "FAILURES!!!" in output or "INSTRUMENTATION_FAILED" in output:
            update_run(status="failed", phase="done", append_output="\nTest FAILED\n")
        elif exit_code != 0:
            update_run(status="failed", phase="done", append_output=f"\nTest FAILED (exit code {exit_code})\n")
        else:
            update_run(status="passed", phase="done", append_output="\nTest PASSED\n")

    except Exception as e:
        update_run(status="error", phase="done", append_output=f"\nUnexpected error: {e}\n")
    finally:
        cleanup()


class RouteBuilderHandler(SimpleHTTPRequestHandler):
    """HTTP request handler for the Route Builder server."""

    def __init__(self, *args, repo_root=None, static_dir=None, **kwargs):
        self.repo_root = repo_root
        self._static_dir = static_dir
        super().__init__(*args, directory=static_dir, **kwargs)

    def _set_cors_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")

    def _send_json(self, status_code, data):
        body = json.dumps(data).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json")
        self._set_cors_headers()
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204)
        self._set_cors_headers()
        self.end_headers()

    def do_GET(self):
        if self.path == "/api/check-device":
            self._handle_check_device()
        elif self.path == "/api/config":
            self._handle_config()
        elif self.path.startswith("/api/run-status/"):
            run_id = self.path[len("/api/run-status/"):]
            self._handle_run_status(run_id)
        elif not self.path.startswith("/api/"):
            # Serve static files
            super().do_GET()
        else:
            self._send_json(404, {"error": "Not found"})

    def do_POST(self):
        if self.path == "/api/run-test":
            self._handle_run_test()
        else:
            self._send_json(404, {"error": "Not found"})

    def _handle_check_device(self):
        connected, device_name = check_device()
        api_key_set = bool(os.environ.get("ROUTES_AI_API_KEY", "") or os.environ.get("PILOT_API_KEY", ""))
        self._send_json(200, {
            "deviceConnected": connected,
            "deviceName": device_name,
            "apiKeySet": api_key_set,
            "appPackageId": config.get("appPackageId", ""),
        })

    def _handle_config(self):
        self._send_json(200, config)

    def _handle_run_status(self, run_id):
        with runs_lock:
            run = runs.get(run_id)
        if run is None:
            self._send_json(404, {"error": "Run not found"})
            return
        self._send_json(200, {
            "status": run["status"],
            "phase": run["phase"],
            "output": run["output"],
        })

    def _handle_run_test(self):
        global current_run_id

        # Check if a run is already in progress
        with current_run_lock:
            if current_run_id is not None:
                self._send_json(409, {"error": "A test run is already in progress"})
                return
            run_id = str(uuid.uuid4())
            current_run_id = run_id

        # Parse request body
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length)
        try:
            data = json.loads(body)
        except (json.JSONDecodeError, ValueError):
            with current_run_lock:
                current_run_id = None
            self._send_json(400, {"error": "Invalid JSON"})
            return

        yaml_content = data.get("yamlContent", "")
        filename = data.get("filename", "")
        test_method_code = data.get("testMethodCode", "")
        method_name = data.get("methodName", "")

        if not all([yaml_content, filename, test_method_code, method_name]):
            with current_run_lock:
                current_run_id = None
            self._send_json(400, {"error": "Missing required fields"})
            return

        # Initialize run state
        with runs_lock:
            runs[run_id] = {
                "status": "running",
                "phase": "writing",
                "output": "",
            }

        # Start background thread
        thread = threading.Thread(
            target=run_test_background,
            args=(run_id, yaml_content, filename, test_method_code, method_name, self.repo_root),
            daemon=True,
        )
        thread.start()

        self._send_json(202, {"runId": run_id})

    def log_message(self, format, *args):
        """Override to prefix log messages."""
        print(f"[server] {format % args}")


def main():
    parser = argparse.ArgumentParser(description="Route Builder HTTP Server")
    parser.add_argument("--port", type=int, default=8080, help="Port to listen on")
    parser.add_argument("--config", required=True, help="Path to pilot.config.json")
    parser.add_argument("--repo-root", required=True, help="Path to the project repo root")
    args = parser.parse_args()

    # Load config
    global config
    config_path = os.path.abspath(args.config)
    if not os.path.isfile(config_path):
        print(f"Error: Config file not found: {config_path}")
        raise SystemExit(1)

    with open(config_path, "r") as f:
        config = json.load(f)

    print(f"Loaded config from {config_path}")

    repo_root = os.path.abspath(args.repo_root)
    static_dir = os.path.dirname(os.path.abspath(__file__))

    handler = partial(RouteBuilderHandler, repo_root=repo_root, static_dir=static_dir)
    server = HTTPServer(("", args.port), handler)

    print(f"Route Builder server running at http://localhost:{args.port}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down server.")
        server.shutdown()


if __name__ == "__main__":
    main()
