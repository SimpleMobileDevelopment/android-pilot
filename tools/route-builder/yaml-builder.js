// yaml-builder.js — Generates Route YAML and Kotlin test methods from form state.

/**
 * Characters that require a YAML string value to be single-quoted.
 */
const YAML_SPECIAL = /[:#{}]/;

/**
 * Return `value` single-quoted if it contains YAML-special characters,
 * doubling any internal single quotes.  Otherwise return it as-is.
 *
 * The `{variable}` interpolation syntax is preserved inside the quotes.
 */
function yamlQuote(value) {
  if (YAML_SPECIAL.test(value) || value.includes("'")) {
    const escaped = value.replace(/'/g, "''");
    return `'${escaped}'`;
  }
  return value;
}

/**
 * Render a single instruction line at the given base indent.
 */
function renderInstruction(inst, indent) {
  if (inst.type === "remember") {
    return [
      `${indent}- remember:`,
      `${indent}    key: ${inst.key}`,
      `${indent}    description: ${yamlQuote(inst.description)}`,
    ].join("\n");
  }

  // action or verify
  return `${indent}- ${inst.type}: ${yamlQuote(inst.value)}`;
}

/**
 * Generate a YAML string from a route state object.
 *
 * @param {object} route
 * @param {string} route.name
 * @param {Array}  route.steps
 * @returns {string} YAML content (with trailing newline)
 */
export function generateYaml(route) {
  const lines = [];

  lines.push(`route: ${yamlQuote(route.name)}`);

  if (route.permissions && route.permissions.length > 0) {
    lines.push("permissions:");
    for (const perm of route.permissions) {
      lines.push(`  - ${perm}`);
    }
  }

  lines.push("");
  lines.push("steps:");

  route.steps.forEach((step, index) => {
    if (index > 0) {
      lines.push("");
    }

    lines.push(`  - step: ${yamlQuote(step.name)}`);

    if (step.timeout != null) {
      lines.push(`    timeout: ${step.timeout}`);
    }

    lines.push("    instructions:");

    for (const inst of step.instructions) {
      lines.push(renderInstruction(inst, "      "));
    }
  });

  return lines.join("\n") + "\n";
}

/**
 * Convert a route name to a kebab-case YAML filename.
 *
 * e.g. "Login with phone" -> "login-with-phone.yaml"
 *
 * @param {string} routeName
 * @returns {string}
 */
export function generateFilename(routeName) {
  const kebab = routeName
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9\s-]/g, "")
    .replace(/\s+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
  return `${kebab}.yaml`;
}

/**
 * Convert a YAML filename to a camelCase test method name.
 *
 * e.g. "login-with-phone.yaml" -> "loginWithPhone"
 *
 * @param {string} filename
 * @returns {string}
 */
export function generateMethodName(filename) {
  const base = filename.replace(/\.yaml$/, "");
  return base.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
}

/**
 * Generate a full Kotlin @Test method string.
 *
 * The output includes a leading blank line before @Test to match the
 * existing style in YamlRouteTest.kt.
 *
 * @param {string} filename  e.g. "login-with-phone.yaml"
 * @returns {string}
 */
export function generateTestMethod(filename) {
  const method = generateMethodName(filename);
  return `\n    @Test\n    fun ${method}(): Unit = runYamlRoute("${filename}")\n`;
}

// ── YAML Parser (YAML → route state) ─────────────────────────────

/**
 * Strip single quotes from a YAML string value, unescaping doubled quotes.
 */
function yamlUnquote(value) {
  let s = value.trim();
  if (s.startsWith("'") && s.endsWith("'") && s.length >= 2) {
    s = s.slice(1, -1).replace(/''/g, "'");
  }
  return s;
}

/**
 * Extract the value after a YAML key on a line.
 * e.g. "  - action: Tap the button" with key "action" → "Tap the button"
 */
function extractValue(line, key) {
  const idx = line.indexOf(`${key}:`);
  if (idx === -1) return null;
  const raw = line.slice(idx + key.length + 1).trim();
  return yamlUnquote(raw);
}

let _parseIdCounter = 0;
function parseId() {
  return `parsed-${Date.now()}-${_parseIdCounter++}`;
}

/**
 * Parse a YAML string into a route state object.
 *
 * @param {string} yamlString
 * @returns {{ name: string, steps: Array }} route state compatible with the form
 * @throws {Error} with user-friendly message if parsing fails
 */
export function parseYaml(yamlString) {
  const lines = yamlString.split('\n');
  let routeName = null;
  const permissions = [];
  let inPermissions = false;
  const steps = [];
  let currentStep = null;
  let expectRememberFields = false;
  let currentRemember = null;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();

    // Skip empty lines and comments
    if (!trimmed || trimmed.startsWith('#')) {
      continue;
    }

    // route: name
    if (/^route\s*:/.test(trimmed)) {
      routeName = yamlUnquote(trimmed.replace(/^route\s*:\s*/, ''));
      inPermissions = false;
      continue;
    }

    // permissions: (root-level marker)
    if (/^permissions\s*:\s*$/.test(trimmed)) {
      inPermissions = true;
      continue;
    }

    // permission list items (indented under permissions:)
    if (inPermissions && /^-\s+/.test(trimmed)) {
      permissions.push(trimmed.replace(/^-\s+/, '').trim());
      continue;
    }

    // Any non-indented key ends the permissions block
    if (inPermissions && /^\S/.test(line)) {
      inPermissions = false;
    }

    // steps: (just a marker, no value)
    if (/^steps\s*:\s*$/.test(trimmed)) {
      inPermissions = false;
      continue;
    }

    // - step: name
    if (/^-\s*step\s*:/.test(trimmed)) {
      // Finish any pending remember
      if (expectRememberFields && currentRemember && currentStep) {
        currentStep.instructions.push(currentRemember);
        expectRememberFields = false;
        currentRemember = null;
      }

      currentStep = {
        id: parseId(),
        name: yamlUnquote(trimmed.replace(/^-\s*step\s*:\s*/, '')),
        timeout: null,
        instructions: []
      };
      steps.push(currentStep);
      continue;
    }

    // timeout: number (inside a step)
    if (/^\s*timeout\s*:/.test(line) && currentStep) {
      const val = trimmed.replace(/^timeout\s*:\s*/, '');
      const num = parseInt(val, 10);
      if (!isNaN(num)) currentStep.timeout = num;
      continue;
    }

    // instructions: (just a marker)
    if (/^\s*instructions\s*:\s*$/.test(line)) {
      continue;
    }

    // Remember fields: key: and description: (following a remember:)
    if (expectRememberFields && currentRemember) {
      if (/^\s*key\s*:/.test(line)) {
        currentRemember.key = yamlUnquote(trimmed.replace(/^key\s*:\s*/, ''));
        continue;
      }
      if (/^\s*description\s*:/.test(line)) {
        currentRemember.description = yamlUnquote(trimmed.replace(/^description\s*:\s*/, ''));
        // Remember is complete
        if (currentStep) currentStep.instructions.push(currentRemember);
        expectRememberFields = false;
        currentRemember = null;
        continue;
      }
    }

    // - action: value
    if (/^-\s*action\s*:/.test(trimmed) && currentStep) {
      // Finish any pending remember
      if (expectRememberFields && currentRemember) {
        currentStep.instructions.push(currentRemember);
        expectRememberFields = false;
        currentRemember = null;
      }
      currentStep.instructions.push({
        id: parseId(),
        type: 'action',
        value: yamlUnquote(trimmed.replace(/^-\s*action\s*:\s*/, '')),
        key: '',
        description: ''
      });
      continue;
    }

    // - verify: value
    if (/^-\s*verify\s*:/.test(trimmed) && currentStep) {
      if (expectRememberFields && currentRemember) {
        currentStep.instructions.push(currentRemember);
        expectRememberFields = false;
        currentRemember = null;
      }
      currentStep.instructions.push({
        id: parseId(),
        type: 'verify',
        value: yamlUnquote(trimmed.replace(/^-\s*verify\s*:\s*/, '')),
        key: '',
        description: ''
      });
      continue;
    }

    // - remember: (starts a remember block)
    if (/^-\s*remember\s*:\s*$/.test(trimmed) && currentStep) {
      if (expectRememberFields && currentRemember) {
        currentStep.instructions.push(currentRemember);
      }
      currentRemember = {
        id: parseId(),
        type: 'remember',
        value: '',
        key: '',
        description: ''
      };
      expectRememberFields = true;
      continue;
    }
  }

  // Finish any trailing remember
  if (expectRememberFields && currentRemember && currentStep) {
    currentStep.instructions.push(currentRemember);
  }

  if (!routeName) {
    throw new Error("Missing 'route:' name at root level");
  }

  if (steps.length === 0) {
    throw new Error("No steps found — add at least one '- step:' entry");
  }

  // Ensure every step has at least one instruction
  for (const step of steps) {
    if (step.instructions.length === 0) {
      step.instructions.push({
        id: parseId(),
        type: 'action',
        value: '',
        key: '',
        description: ''
      });
    }
  }

  return { name: routeName, permissions, steps };
}
