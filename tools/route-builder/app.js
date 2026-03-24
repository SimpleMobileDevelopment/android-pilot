import { generateYaml, generateFilename, generateMethodName, generateTestMethod, parseYaml } from './yaml-builder.js';
import { loadConfig, GitHubClient } from './github.js';

// ── Utility ──────────────────────────────────────────────────────────

let idCounter = 0;
function generateId() {
  return `id-${Date.now()}-${idCounter++}`;
}

// ── State ────────────────────────────────────────────────────────────

let state = {
  route: {
    name: '',
    permissions: [],
    steps: [
      {
        id: generateId(),
        name: '',
        timeout: null,
        instructions: [
          { id: generateId(), type: 'action', value: '', key: '', description: '' }
        ]
      }
    ]
  },
  github: {
    token: null,
    client: null,
    user: null,
    connected: false
  },
  editorMode: 'visual', // 'visual' | 'yaml'
  submission: {
    status: 'idle',
    prUrl: null,
    branchName: null,
    testStatus: null,
    testRunUrl: null,
    error: null
  }
};

let pollTimer = null;
let stepsSortable = null;
const instructionSortables = new Map();

// ── DOM references ───────────────────────────────────────────────────

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => document.querySelectorAll(sel);

// ── Initialization ───────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', async () => {
  await loadConfig();
  bindStaticEvents();
  restoreSession();
  checkLocalDevice();
  render();
  updateYamlPreview();
});

function bindStaticEvents() {
  // Auth toggle
  $('#auth-toggle').addEventListener('click', () => {
    const panel = $('#auth-panel');
    const chevron = $('#auth-chevron');
    panel.classList.toggle('hidden');
    chevron.classList.toggle('open');
  });

  // GitHub connect
  $('#github-connect-btn').addEventListener('click', connectGitHub);
  $('#github-pat').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') connectGitHub();
  });

  // Route name
  $('#route-name').addEventListener('input', (e) => {
    state.route.name = e.target.value;
    updateYamlPreview();
  });

  // Permissions toggle
  $('#permissions-toggle').addEventListener('click', () => {
    $('#permissions-panel').classList.toggle('hidden');
    $('#permissions-chevron').classList.toggle('open');
  });

  // Permission checkboxes
  $$('.permission-checkbox').forEach((cb) => {
    cb.addEventListener('change', () => {
      syncPermissionsFromCheckboxes();
      updateYamlPreview();
    });
  });

  // Add step
  $('#add-step-btn').addEventListener('click', addStep);

  // Copy YAML
  $('#copy-yaml-btn').addEventListener('click', copyYaml);

  // Editor tabs
  $('#tab-visual').addEventListener('click', () => switchEditorMode('visual'));
  $('#tab-yaml').addEventListener('click', () => switchEditorMode('yaml'));

  // YAML editor input (debounced)
  let yamlEditorTimer = null;
  $('#yaml-editor').addEventListener('input', () => {
    clearTimeout(yamlEditorTimer);
    yamlEditorTimer = setTimeout(handleYamlEditorInput, 300);
  });

  // Submit and Run Test
  $('#submit-btn').addEventListener('click', handleSubmit);
  $('#run-test-btn').addEventListener('click', handleSaveAndRunTest);
  $('#run-local-btn').addEventListener('click', handleRunLocally);

  // Log toggle
  $('#log-toggle').addEventListener('click', () => {
    $('#log-output').classList.toggle('hidden');
    $('#log-chevron').classList.toggle('open');
  });
}

// ── Local Device Check ───────────────────────────────────────────────

async function checkLocalDevice() {
  try {
    const resp = await fetch('/api/check-device');
    const data = await resp.json();
    const badge = $('#device-badge');
    if (data.deviceConnected) {
      badge.textContent = `Device: ${data.deviceName}`;
      badge.className = 'ml-2 px-2 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800';
    } else {
      badge.textContent = 'No device';
      badge.className = 'ml-2 px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-500';
    }
    if (!data.apiKeySet) {
      const warn = $('#api-key-warning');
      if (warn) { warn.classList.remove('hidden'); }
    }
  } catch {
    // Server might not support API — device check is best-effort
  }
}

// ── GitHub Auth ──────────────────────────────────────────────────────

function restoreSession() {
  const token = sessionStorage.getItem('github_pat');
  if (token) {
    state.github.token = token;
    state.github.client = new GitHubClient(token);
    // Silently verify
    state.github.client.getUser().then((user) => {
      state.github.user = user;
      state.github.connected = true;
      updateAuthUI();
    }).catch(() => {
      sessionStorage.removeItem('github_pat');
      state.github.token = null;
      state.github.client = null;
    });
  }
}

async function connectGitHub() {
  const pat = $('#github-pat').value.trim();
  if (!pat) return;

  const btn = $('#github-connect-btn');
  btn.textContent = 'Connecting...';
  btn.disabled = true;
  $('#auth-error').classList.add('hidden');

  try {
    const client = new GitHubClient(pat);
    const user = await client.getUser();
    state.github = { token: pat, client, user, connected: true };
    sessionStorage.setItem('github_pat', pat);
    updateAuthUI();
  } catch (err) {
    $('#auth-error').textContent = `Connection failed: ${err.message}`;
    $('#auth-error').classList.remove('hidden');
  } finally {
    btn.textContent = 'Connect';
    btn.disabled = false;
  }
}

function updateAuthUI() {
  const badge = $('#auth-badge');
  const badgeDisc = $('#auth-badge-disconnected');
  if (state.github.connected && state.github.user) {
    badge.textContent = state.github.user.login;
    badge.classList.remove('hidden');
    badgeDisc.classList.add('hidden');
  } else {
    badge.classList.add('hidden');
    badgeDisc.classList.remove('hidden');
  }
}

// ── Permissions ──────────────────────────────────────────────────

function syncPermissionsFromCheckboxes() {
  const perms = [];
  $$('.permission-checkbox').forEach((cb) => {
    if (cb.checked) perms.push(cb.value);
  });
  state.route.permissions = perms;
  updatePermissionsCount();
}

function syncPermissionsToCheckboxes() {
  const perms = state.route.permissions || [];
  $$('.permission-checkbox').forEach((cb) => {
    cb.checked = perms.includes(cb.value);
  });
  updatePermissionsCount();
}

function updatePermissionsCount() {
  const count = (state.route.permissions || []).length;
  const badge = $('#permissions-count');
  if (count > 0) {
    badge.textContent = count;
    badge.classList.remove('hidden');
  } else {
    badge.classList.add('hidden');
  }
}

// ── Render ───────────────────────────────────────────────────────────

function render() {
  const container = $('#steps-container');
  container.innerHTML = '';

  state.route.steps.forEach((step, stepIndex) => {
    container.appendChild(renderStepCard(step, stepIndex));
  });

  // Set route name input value if it differs
  const nameInput = $('#route-name');
  if (nameInput.value !== state.route.name) {
    nameInput.value = state.route.name;
  }

  // Sync permission checkboxes from state
  syncPermissionsToCheckboxes();

  initSortables();
}

function renderStepCard(step, stepIndex) {
  const card = document.createElement('div');
  card.className = 'step-card bg-white border border-gray-200 rounded-lg shadow-sm p-4';
  card.dataset.stepId = step.id;

  card.innerHTML = `
    <div class="flex items-start gap-3 mb-3">
      <div class="drag-handle step-drag-handle text-lg mt-1" title="Drag to reorder">&#x2807;</div>
      <div class="flex-1 space-y-2">
        <div class="flex items-center gap-2">
          <span class="text-xs font-semibold text-gray-400 uppercase tracking-wide">Step ${stepIndex + 1}</span>
          <button class="delete-step-btn ml-auto text-gray-400 hover:text-red-500 transition-colors p-1" data-step-id="${step.id}" title="Delete step">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/></svg>
          </button>
        </div>
        <input type="text" class="step-name-input w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none"
               placeholder="Step name, e.g. &quot;Navigate to settings&quot;"
               value="${escapeAttr(step.name)}"
               data-step-id="${step.id}">
        <div class="flex items-center gap-2">
          <label class="text-xs text-gray-500">Timeout (seconds):</label>
          <input type="number" class="step-timeout-input w-20 px-2 py-1 border border-gray-300 rounded-md text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none"
                 placeholder="--"
                 min="1"
                 value="${step.timeout != null ? step.timeout : ''}"
                 data-step-id="${step.id}">
        </div>
      </div>
    </div>

    <div class="ml-7">
      <p class="text-xs font-medium text-gray-500 mb-2 uppercase tracking-wide">Instructions</p>
      <div class="instructions-container space-y-2" data-step-id="${step.id}">
        ${step.instructions.map((inst) => renderInstructionHTML(step.id, inst)).join('')}
      </div>
      <button class="add-instruction-btn mt-2 text-sm text-indigo-600 hover:text-indigo-800 font-medium transition-colors"
              data-step-id="${step.id}">
        + Add Instruction
      </button>
    </div>
  `;

  // Bind events
  card.querySelector('.delete-step-btn').addEventListener('click', () => removeStep(step.id));
  card.querySelector('.step-name-input').addEventListener('input', (e) => {
    step.name = e.target.value;
    updateYamlPreview();
  });
  card.querySelector('.step-timeout-input').addEventListener('input', (e) => {
    const val = e.target.value.trim();
    step.timeout = val ? parseInt(val, 10) : null;
    updateYamlPreview();
  });
  card.querySelector('.add-instruction-btn').addEventListener('click', () => addInstruction(step.id));

  // Bind instruction events
  bindInstructionEvents(card, step);

  return card;
}

function renderInstructionHTML(stepId, inst) {
  const isRemember = inst.type === 'remember';
  return `
    <div class="instruction-row flex items-start gap-2 bg-gray-50 rounded-md p-2 border border-gray-100" data-instruction-id="${inst.id}" data-step-id="${stepId}">
      <div class="drag-handle instruction-drag-handle text-sm mt-1" title="Drag to reorder">&#x2807;</div>
      <select class="instruction-type-select px-2 py-1.5 border border-gray-300 rounded-md text-sm bg-white focus:ring-2 focus:ring-indigo-500 outline-none"
              data-step-id="${stepId}" data-instruction-id="${inst.id}">
        <option value="action" ${inst.type === 'action' ? 'selected' : ''}>action</option>
        <option value="verify" ${inst.type === 'verify' ? 'selected' : ''}>verify</option>
        <option value="remember" ${inst.type === 'remember' ? 'selected' : ''}>remember</option>
      </select>
      ${isRemember ? `
        <input type="text" class="instruction-key-input flex-1 px-2 py-1.5 border border-gray-300 rounded-md text-sm focus:ring-2 focus:ring-indigo-500 outline-none"
               placeholder="Variable key"
               value="${escapeAttr(inst.key)}"
               data-step-id="${stepId}" data-instruction-id="${inst.id}">
        <input type="text" class="instruction-desc-input flex-1 px-2 py-1.5 border border-gray-300 rounded-md text-sm focus:ring-2 focus:ring-indigo-500 outline-none"
               placeholder="What to remember"
               value="${escapeAttr(inst.description)}"
               data-step-id="${stepId}" data-instruction-id="${inst.id}">
      ` : `
        <input type="text" class="instruction-value-input flex-1 px-2 py-1.5 border border-gray-300 rounded-md text-sm focus:ring-2 focus:ring-indigo-500 outline-none"
               placeholder="${inst.type === 'verify' ? 'Describe what to verify...' : 'Describe the action...'}"
               value="${escapeAttr(inst.value)}"
               data-step-id="${stepId}" data-instruction-id="${inst.id}">
      `}
      <button class="delete-instruction-btn text-gray-400 hover:text-red-500 transition-colors p-1 mt-0.5" data-step-id="${stepId}" data-instruction-id="${inst.id}" title="Delete instruction">
        <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg>
      </button>
    </div>
  `;
}

function bindInstructionEvents(card, step) {
  card.querySelectorAll('.instruction-row').forEach((row) => {
    const instId = row.dataset.instructionId;
    const inst = step.instructions.find((i) => i.id === instId);
    if (!inst) return;

    row.querySelector('.instruction-type-select').addEventListener('change', (e) => {
      inst.type = e.target.value;
      // Re-render this step to swap input fields
      render();
      updateYamlPreview();
    });

    const valueInput = row.querySelector('.instruction-value-input');
    if (valueInput) {
      valueInput.addEventListener('input', (e) => {
        inst.value = e.target.value;
        updateYamlPreview();
      });
    }

    const keyInput = row.querySelector('.instruction-key-input');
    if (keyInput) {
      keyInput.addEventListener('input', (e) => {
        inst.key = e.target.value;
        updateYamlPreview();
      });
    }

    const descInput = row.querySelector('.instruction-desc-input');
    if (descInput) {
      descInput.addEventListener('input', (e) => {
        inst.description = e.target.value;
        updateYamlPreview();
      });
    }

    row.querySelector('.delete-instruction-btn').addEventListener('click', () => {
      removeInstruction(step.id, instId);
    });
  });
}

// ── Step / Instruction CRUD ──────────────────────────────────────────

function addStep() {
  state.route.steps.push({
    id: generateId(),
    name: '',
    timeout: null,
    instructions: [
      { id: generateId(), type: 'action', value: '', key: '', description: '' }
    ]
  });
  render();
  updateYamlPreview();
  // Focus the new step's name input
  const cards = $$('.step-name-input');
  const last = cards[cards.length - 1];
  if (last) last.focus();
}

function removeStep(stepId) {
  if (state.route.steps.length <= 1) return; // Keep at least one step
  const card = document.querySelector(`[data-step-id="${stepId}"].step-card`);
  if (card) {
    card.classList.add('removing');
    setTimeout(() => {
      state.route.steps = state.route.steps.filter((s) => s.id !== stepId);
      render();
      updateYamlPreview();
    }, 200);
  } else {
    state.route.steps = state.route.steps.filter((s) => s.id !== stepId);
    render();
    updateYamlPreview();
  }
}

function addInstruction(stepId) {
  const step = state.route.steps.find((s) => s.id === stepId);
  if (!step) return;
  step.instructions.push({
    id: generateId(),
    type: 'action',
    value: '',
    key: '',
    description: ''
  });
  render();
  updateYamlPreview();
  // Focus the new instruction's value input
  const container = document.querySelector(`.instructions-container[data-step-id="${stepId}"]`);
  if (container) {
    const inputs = container.querySelectorAll('.instruction-value-input');
    const last = inputs[inputs.length - 1];
    if (last) last.focus();
  }
}

function removeInstruction(stepId, instructionId) {
  const step = state.route.steps.find((s) => s.id === stepId);
  if (!step || step.instructions.length <= 1) return; // Keep at least one
  step.instructions = step.instructions.filter((i) => i.id !== instructionId);
  render();
  updateYamlPreview();
}

// ── SortableJS ───────────────────────────────────────────────────────

function initSortables() {
  // Destroy old sortables
  if (stepsSortable) stepsSortable.destroy();
  instructionSortables.forEach((s) => s.destroy());
  instructionSortables.clear();

  // Steps sortable
  const stepsContainer = $('#steps-container');
  if (stepsContainer) {
    stepsSortable = new Sortable(stepsContainer, {
      handle: '.step-drag-handle',
      animation: 200,
      ghostClass: 'sortable-ghost',
      chosenClass: 'sortable-chosen',
      onEnd: (evt) => {
        const { oldIndex, newIndex } = evt;
        if (oldIndex === newIndex) return;
        const [moved] = state.route.steps.splice(oldIndex, 1);
        state.route.steps.splice(newIndex, 0, moved);
        render();
        updateYamlPreview();
      }
    });
  }

  // Instructions sortables
  document.querySelectorAll('.instructions-container').forEach((container) => {
    const stepId = container.dataset.stepId;
    const sortable = new Sortable(container, {
      handle: '.instruction-drag-handle',
      animation: 150,
      ghostClass: 'sortable-ghost',
      onEnd: (evt) => {
        const { oldIndex, newIndex } = evt;
        if (oldIndex === newIndex) return;
        const step = state.route.steps.find((s) => s.id === stepId);
        if (!step) return;
        const [moved] = step.instructions.splice(oldIndex, 1);
        step.instructions.splice(newIndex, 0, moved);
        render();
        updateYamlPreview();
      }
    });
    instructionSortables.set(stepId, sortable);
  });
}

// ── YAML Preview ─────────────────────────────────────────────────────

function updateYamlPreview() {
  const yaml = generateYaml(state.route);

  if (state.editorMode === 'visual') {
    const highlighted = highlightYaml(yaml);
    $('#yaml-preview').innerHTML = highlighted;
  }
  // In yaml mode, don't overwrite the textarea (user is typing)

  const filename = generateFilename(state.route.name || 'untitled');
  $('#filename-preview').textContent = filename;

  const method = generateTestMethod(filename);
  $('#method-preview').textContent = method.trim();
}

function highlightYaml(yaml) {
  return yaml
    .split('\n')
    .map((line) => {
      const safe = escapeHtml(line);
      // route:, steps:, or permissions: root keys
      if (/^(route|steps|permissions):/.test(line)) {
        return safe.replace(/^(\w+:)(.*)/, '<span class="yaml-key">$1</span>$2');
      }
      // permission list items (e.g. "  - android.permission.CAMERA")
      if (/^\s+- android\.permission\./.test(line)) {
        return safe.replace(/(- )(.*)/, '<span class="yaml-string">$1$2</span>');
      }
      // step: name
      if (/^\s+- step:/.test(line)) {
        return safe.replace(/(- step:)(.*)/, '<span class="yaml-key">$1</span><span class="yaml-step-name">$2</span>');
      }
      // timeout / instructions keys
      if (/^\s+(timeout|instructions):/.test(line)) {
        return safe.replace(/(\w+:)/, '<span class="yaml-key">$1</span>');
      }
      // action:
      if (/^\s+- action:/.test(line)) {
        return safe.replace(/(- action:)(.*)/, '<span class="yaml-keyword-action">$1</span><span class="yaml-string">$2</span>');
      }
      // verify:
      if (/^\s+- verify:/.test(line)) {
        return safe.replace(/(- verify:)(.*)/, '<span class="yaml-keyword-verify">$1</span><span class="yaml-string">$2</span>');
      }
      // remember:
      if (/^\s+- remember:/.test(line)) {
        return safe.replace(/(- remember:)/, '<span class="yaml-keyword-remember">$1</span>');
      }
      // key: / description: inside remember
      if (/^\s+(key|description):/.test(line)) {
        return safe.replace(/(\w+:)(.*)/, '<span class="yaml-keyword-remember">$1</span><span class="yaml-string">$2</span>');
      }
      return safe;
    })
    .join('\n');
}

function copyYaml() {
  const yaml = generateYaml(state.route);
  navigator.clipboard.writeText(yaml).then(() => {
    const btn = $('#copy-yaml-btn');
    btn.textContent = 'Copied!';
    setTimeout(() => { btn.textContent = 'Copy'; }, 1500);
  });
}

// ── Editor Mode ──────────────────────────────────────────────────────

function switchEditorMode(mode) {
  if (mode === state.editorMode) return;

  const previewContainer = $('#yaml-preview-container');
  const editor = $('#yaml-editor');
  const tabVisual = $('#tab-visual');
  const tabYaml = $('#tab-yaml');
  const parseError = $('#yaml-parse-error');

  if (mode === 'yaml') {
    // Switch to YAML edit mode
    editor.value = generateYaml(state.route);
    previewContainer.classList.add('hidden');
    editor.classList.remove('hidden');
    parseError.classList.add('hidden');
    tabVisual.classList.remove('active');
    tabYaml.classList.add('active');
    editor.focus();
  } else {
    // Switch to visual mode — parse YAML first
    const yamlText = editor.value;
    try {
      const parsed = parseYaml(yamlText);
      state.route = parsed;
      parseError.classList.add('hidden');
    } catch (err) {
      // Show error but still switch
      parseError.textContent = err.message;
      parseError.classList.remove('hidden');
    }
    previewContainer.classList.remove('hidden');
    editor.classList.add('hidden');
    tabYaml.classList.remove('active');
    tabVisual.classList.add('active');
    render();
    updateYamlPreview();
  }

  state.editorMode = mode;
}

function handleYamlEditorInput() {
  const yamlText = $('#yaml-editor').value;
  const parseError = $('#yaml-parse-error');

  try {
    const parsed = parseYaml(yamlText);
    state.route = parsed;
    parseError.classList.add('hidden');

    // Update filename and method previews (but NOT the textarea — avoid cursor jump)
    const filename = generateFilename(state.route.name || 'untitled');
    $('#filename-preview').textContent = filename;
    const method = generateTestMethod(filename);
    $('#method-preview').textContent = method.trim();
  } catch (err) {
    parseError.textContent = err.message;
    parseError.classList.remove('hidden');
  }
}

// ── Validation ───────────────────────────────────────────────────────

function validate() {
  const errors = [];

  if (!state.route.name.trim()) {
    errors.push('Route name is required.');
  }

  if (state.route.steps.length === 0) {
    errors.push('At least one step is required.');
  }

  state.route.steps.forEach((step, i) => {
    const label = `Step ${i + 1}`;
    if (!step.name.trim()) {
      errors.push(`${label}: Name is required.`);
    }
    if (step.instructions.length === 0) {
      errors.push(`${label}: At least one instruction is required.`);
    }
    step.instructions.forEach((inst, j) => {
      const instLabel = `${label}, Instruction ${j + 1}`;
      if (inst.type === 'remember') {
        if (!inst.key.trim()) errors.push(`${instLabel}: Variable key is required.`);
        if (!inst.description.trim()) errors.push(`${instLabel}: Description is required.`);
      } else {
        if (!inst.value.trim()) errors.push(`${instLabel}: Value is required.`);
      }
    });
  });

  return errors;
}

function showValidationErrors(errors) {
  const container = $('#validation-errors');
  const list = $('#validation-error-list');
  if (errors.length === 0) {
    container.classList.add('hidden');
    return;
  }
  list.innerHTML = errors.map((e) => `<li>${escapeHtml(e)}</li>`).join('');
  container.classList.remove('hidden');
}

// ── Submit (Create PR) ──────────────────────────────────────────────

async function handleSubmit() {
  const errors = validate();
  showValidationErrors(errors);
  if (errors.length > 0) return;

  if (!state.github.connected) {
    showValidationErrors(['Please connect to GitHub first.']);
    return;
  }

  const btn = $('#submit-btn');
  const statusEl = $('#submission-status');
  statusEl.classList.remove('hidden');

  try {
    btn.disabled = true;

    // If branch already exists (from a prior Save & Run), just create the PR
    if (state.submission.branchName) {
      btn.textContent = 'Creating PR...';
      statusEl.innerHTML = renderStatusMessage('Creating pull request from existing branch...', 'info');

      const yamlContent = generateYaml(state.route);
      const { prUrl } = await state.github.client.createPR({
        routeName: state.route.name,
        yamlContent,
        branchName: state.submission.branchName
      });

      state.submission.status = 'done';
      state.submission.prUrl = prUrl;
      btn.textContent = 'Pull Request Created';
      statusEl.innerHTML = renderPrSuccess(prUrl);
      return;
    }

    // No branch yet — validate filename, then save + PR in one step
    state.submission.status = 'validating';
    btn.textContent = 'Validating...';
    statusEl.innerHTML = renderStatusMessage('Checking for filename collisions...', 'info');

    const filename = generateFilename(state.route.name);
    const existing = await state.github.client.getExistingRouteFiles();
    if (existing.includes(filename)) {
      showValidationErrors([`A route file named "${filename}" already exists. Please choose a different route name.`]);
      btn.disabled = false;
      btn.textContent = 'Create Pull Request';
      statusEl.classList.add('hidden');
      return;
    }

    state.submission.status = 'creating';
    btn.textContent = 'Creating PR...';
    statusEl.innerHTML = renderStatusMessage('Creating branch, committing files, and opening PR...', 'info');

    const yamlContent = generateYaml(state.route);
    const testMethodCode = generateTestMethod(filename);

    const result = await state.github.client.createRoutePR({
      routeName: state.route.name,
      yamlContent,
      testMethodCode,
      filename
    });

    state.submission.status = 'done';
    state.submission.prUrl = result.prUrl;
    state.submission.branchName = result.branchName;
    btn.textContent = 'Pull Request Created';
    statusEl.innerHTML = renderPrSuccess(result.prUrl);
  } catch (err) {
    state.submission.status = 'error';
    state.submission.error = err.message;
    btn.disabled = false;
    btn.textContent = 'Create Pull Request';
    statusEl.innerHTML = renderStatusMessage(`Error: ${err.message}`, 'error');
  }
}

function renderPrSuccess(prUrl) {
  return `
    <div class="p-4 bg-green-50 border border-green-200 rounded-lg">
      <p class="text-sm text-green-800 font-medium">Pull request created successfully!</p>
      <a href="${escapeAttr(prUrl)}" target="_blank" rel="noopener" class="inline-block mt-1 text-sm text-indigo-600 hover:text-indigo-800 underline">${escapeHtml(prUrl)}</a>
    </div>
  `;
}

// ── Save & Run Test ──────────────────────────────────────────────────

async function handleSaveAndRunTest() {
  const errors = validate();
  showValidationErrors(errors);
  if (errors.length > 0) return;

  if (!state.github.connected) {
    showValidationErrors(['Please connect to GitHub first.']);
    return;
  }

  const runBtn = $('#run-test-btn');
  const statusEl = $('#submission-status');
  statusEl.classList.remove('hidden');

  try {
    runBtn.disabled = true;

    // Save to branch if not already saved
    if (!state.submission.branchName) {
      runBtn.textContent = 'Validating...';
      statusEl.innerHTML = renderStatusMessage('Checking for filename collisions...', 'info');

      const filename = generateFilename(state.route.name);
      const existing = await state.github.client.getExistingRouteFiles();
      if (existing.includes(filename)) {
        showValidationErrors([`A route file named "${filename}" already exists. Please choose a different route name.`]);
        runBtn.disabled = false;
        runBtn.textContent = 'Save & Run Test';
        statusEl.classList.add('hidden');
        return;
      }

      runBtn.textContent = 'Saving to branch...';
      statusEl.innerHTML = renderStatusMessage('Creating branch and committing files...', 'info');

      const yamlContent = generateYaml(state.route);
      const testMethodCode = generateTestMethod(filename);

      const { branchName } = await state.github.client.saveToBranch({
        routeName: state.route.name,
        yamlContent,
        testMethodCode,
        filename
      });

      state.submission.branchName = branchName;
    }

    // Dispatch workflow
    runBtn.textContent = 'Dispatching test...';
    statusEl.innerHTML = renderStatusMessage('Dispatching test workflow on Firebase Test Lab...', 'info');

    const filename = generateFilename(state.route.name);
    const methodName = generateMethodName(filename);

    await state.github.client.dispatchTestRun({
      branchName: state.submission.branchName,
      testMethodName: methodName
    });

    state.submission.testStatus = 'polling';
    runBtn.textContent = 'Test Running...';
    statusEl.innerHTML = `
      <div class="p-4 bg-amber-50 border border-amber-200 rounded-lg space-y-2">
        <div class="flex items-center gap-2 text-sm text-amber-800">
          <span class="status-pulse"></span>
          <span>Test dispatched — waiting for results (polling every 30s)...</span>
        </div>
        <div id="test-status"></div>
      </div>
    `;

    pollTestStatus(runBtn);
  } catch (err) {
    state.submission.testStatus = null;
    runBtn.disabled = false;
    runBtn.textContent = 'Save & Run Test';
    statusEl.innerHTML = renderStatusMessage(`Error: ${err.message}`, 'error');
  }
}

function pollTestStatus(runBtn) {
  if (pollTimer) clearInterval(pollTimer);

  pollTimer = setInterval(async () => {
    try {
      const run = await state.github.client.getLatestWorkflowRun(state.submission.branchName);
      const testStatusEl = document.getElementById('test-status');
      if (!testStatusEl) { clearInterval(pollTimer); return; }

      if (!run) {
        testStatusEl.innerHTML = `
          <div class="flex items-center gap-2 text-sm text-amber-700">
            <span class="status-pulse"></span>
            <span>Waiting for workflow run to appear...</span>
          </div>
        `;
        return;
      }

      if (run.status === 'completed') {
        clearInterval(pollTimer);
        pollTimer = null;
        const isSuccess = run.conclusion === 'success';
        state.submission.testStatus = isSuccess ? 'success' : 'failure';
        state.submission.testRunUrl = run.htmlUrl;

        const colorClass = isSuccess ? 'text-green-700' : 'text-red-700';
        const pulseClass = isSuccess ? 'success' : 'failure';
        const label = isSuccess ? 'Test passed!' : `Test failed (${run.conclusion})`;

        testStatusEl.innerHTML = `
          <div class="flex items-center gap-2 text-sm ${colorClass}">
            <span class="status-pulse ${pulseClass}"></span>
            <span>${label}</span>
            <a href="${run.htmlUrl}" target="_blank" rel="noopener" class="text-indigo-600 hover:text-indigo-800 underline ml-2">View run</a>
          </div>
        `;

        if (runBtn) { runBtn.disabled = false; runBtn.textContent = 'Run Test Again'; }
      } else {
        testStatusEl.innerHTML = `
          <div class="flex items-center gap-2 text-sm text-amber-700">
            <span class="status-pulse"></span>
            <span>Test running (${escapeHtml(run.status)})...</span>
            <a href="${run.htmlUrl}" target="_blank" rel="noopener" class="text-indigo-600 hover:text-indigo-800 underline ml-2">View run</a>
          </div>
        `;
      }
    } catch (err) {
      console.warn('Poll error:', err.message);
    }
  }, 30000);
}

// ── Run Locally ──────────────────────────────────────────────────────

async function handleRunLocally() {
  const errors = validate();
  showValidationErrors(errors);
  if (errors.length > 0) return;

  const btn = $('#run-local-btn');
  const statusEl = $('#submission-status');
  const logPanel = $('#log-panel');
  const logOutput = $('#log-output');
  statusEl.classList.remove('hidden');
  logPanel.classList.remove('hidden');
  logOutput.classList.remove('hidden');
  logOutput.textContent = '';

  try {
    btn.disabled = true;
    btn.textContent = 'Starting...';

    const filename = generateFilename(state.route.name);
    const testMethodCode = generateTestMethod(filename);
    const methodName = generateMethodName(filename);

    const resp = await fetch('/api/run-test', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        yamlContent: generateYaml(state.route),
        filename,
        testMethodCode,
        methodName
      })
    });

    if (resp.status === 409) {
      statusEl.innerHTML = renderStatusMessage('A test is already running. Wait for it to finish.', 'error');
      btn.disabled = false;
      btn.textContent = 'Run Locally';
      return;
    }

    const { runId } = await resp.json();
    pollLocalTestStatus(runId, btn, statusEl, logOutput);
  } catch (err) {
    btn.disabled = false;
    btn.textContent = 'Run Locally';
    statusEl.innerHTML = renderStatusMessage(`Error: ${err.message}`, 'error');
  }
}

function pollLocalTestStatus(runId, btn, statusEl, logOutput) {
  const phaseLabels = {
    writing: 'Preparing test files...',
    building: 'Building APKs (this may take a few minutes)...',
    installing: 'Installing on device...',
    running: 'Running test on device...',
    done: 'Complete'
  };

  const poll = async () => {
    try {
      const resp = await fetch(`/api/run-status/${runId}`);
      if (!resp.ok) return;
      const data = await resp.json();

      // Update log output
      if (data.output) {
        logOutput.textContent = data.output;
        logOutput.scrollTop = logOutput.scrollHeight;
      }

      // Update phase display
      const phaseText = phaseLabels[data.phase] || data.phase;

      if (data.status === 'running') {
        statusEl.innerHTML = `
          <div class="p-3 bg-blue-50 border border-blue-200 rounded-lg">
            <div class="flex items-center gap-2 text-sm text-blue-800">
              <span class="status-pulse"></span>
              <span>${escapeHtml(phaseText)}</span>
            </div>
          </div>
        `;
        setTimeout(poll, 3000);
      } else if (data.status === 'passed') {
        statusEl.innerHTML = `
          <div class="p-3 bg-green-50 border border-green-200 rounded-lg">
            <div class="flex items-center gap-2 text-sm text-green-800">
              <span class="status-pulse success"></span>
              <span>Test passed!</span>
            </div>
          </div>
        `;
        btn.disabled = false;
        btn.textContent = 'Run Locally Again';
      } else {
        statusEl.innerHTML = `
          <div class="p-3 bg-red-50 border border-red-200 rounded-lg">
            <div class="flex items-center gap-2 text-sm text-red-800">
              <span class="status-pulse failure"></span>
              <span>Test failed — check the build output below</span>
            </div>
          </div>
        `;
        btn.disabled = false;
        btn.textContent = 'Run Locally Again';
      }
    } catch {
      setTimeout(poll, 3000);
    }
  };

  setTimeout(poll, 2000); // First poll after 2s
}

// ── Helpers ──────────────────────────────────────────────────────────

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

function escapeAttr(str) {
  return str
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function renderStatusMessage(text, type) {
  const styles = {
    info: 'bg-blue-50 border-blue-200 text-blue-800',
    error: 'bg-red-50 border-red-200 text-red-800',
    success: 'bg-green-50 border-green-200 text-green-800'
  };
  return `<div class="p-3 border rounded-lg text-sm ${styles[type] || styles.info}">${escapeHtml(text)}</div>`;
}