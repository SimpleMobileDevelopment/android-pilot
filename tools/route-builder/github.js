const API_BASE = 'https://api.github.com';

let config = null;

export async function loadConfig() {
    const resp = await fetch('/api/config');
    config = await resp.json();
    return config;
}

function toKebabCase(name) {
  return name
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

function randomSuffix(len = 4) {
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
  let result = '';
  for (let i = 0; i < len; i++) {
    result += chars[Math.floor(Math.random() * chars.length)];
  }
  return result;
}

export class GitHubClient {
  constructor(token) {
    this._token = token;
    if (!config) throw new Error('Call loadConfig() before creating GitHubClient');
  }

  // ── internal helpers ──────────────────────────────────────────────

  _headers() {
    return {
      Authorization: `Bearer ${this._token}`,
      Accept: 'application/vnd.github+json',
      'Content-Type': 'application/json',
    };
  }

  _owner() {
    return config.github.owner;
  }

  _repo() {
    return config.github.repo;
  }

  async _request(method, path, body) {
    const url = `${API_BASE}${path}`;
    const opts = { method, headers: this._headers() };
    if (body !== undefined) {
      opts.body = JSON.stringify(body);
    }

    let response;
    try {
      response = await fetch(url, opts);
    } catch (err) {
      throw new Error(`Network error for ${method} ${path}: ${err.message}`);
    }

    if (!response.ok) {
      let detail = '';
      try {
        detail = await response.text();
      } catch {
        // ignore
      }
      throw new Error(
        `GitHub API ${method} ${path} failed (${response.status}): ${detail}`,
      );
    }

    if (response.status === 204) return null;
    return response.json();
  }

  // ── public API ────────────────────────────────────────────────────

  async getUser() {
    const data = await this._request('GET', '/user');
    return { login: data.login, name: data.name };
  }

  async getExistingRouteFiles() {
    try {
      const data = await this._request(
        'GET',
        `/repos/${this._owner()}/${this._repo()}/contents/${config.yamlDir}?ref=main`,
      );
      return data.filter((f) => f.type === 'file').map((f) => f.name);
    } catch (err) {
      // If the directory doesn't exist yet, return empty
      if (err.message.includes('404')) return [];
      throw err;
    }
  }

  async getTestFile() {
    const data = await this._request(
      'GET',
      `/repos/${this._owner()}/${this._repo()}/contents/${config.testFile}?ref=main`,
    );
    const content = atob(data.content.replace(/\n/g, ''));
    return { content, sha: data.sha };
  }

  async saveToBranch({ routeName, yamlContent, testMethodCode, filename }) {
    const slug = toKebabCase(routeName);
    let suffix = randomSuffix();
    let branchName = `route-test/${slug}-${suffix}`;

    // 1. Get main branch SHA
    const mainRef = await this._request(
      'GET',
      `/repos/${this._owner()}/${this._repo()}/git/ref/heads/main`,
    );
    const mainSha = mainRef.object.sha;

    // 2. Create branch (retry once on 409 conflict)
    try {
      await this._request('POST', `/repos/${this._owner()}/${this._repo()}/git/refs`, {
        ref: `refs/heads/${branchName}`,
        sha: mainSha,
      });
    } catch (err) {
      if (err.message.includes('409') || err.message.includes('422')) {
        suffix = randomSuffix();
        branchName = `route-test/${slug}-${suffix}`;
        await this._request('POST', `/repos/${this._owner()}/${this._repo()}/git/refs`, {
          ref: `refs/heads/${branchName}`,
          sha: mainSha,
        });
      } else {
        throw err;
      }
    }

    // 3. Get current YamlRouteTest.kt content
    const testFileData = await this._request(
      'GET',
      `/repos/${this._owner()}/${this._repo()}/contents/${config.testFile}?ref=main`,
    );
    const kotlinContent = atob(testFileData.content.replace(/\n/g, ''));

    // 4. Modify YamlRouteTest.kt
    if (!kotlinContent.includes(config.testMarker)) {
      throw new Error(
        `YamlRouteTest.kt does not contain the expected marker: "${config.testMarker}"`,
      );
    }

    // Find the companion object line and insert before its preceding blank line
    const companionPattern = config.insertBefore;
    const companionIdx = kotlinContent.indexOf(companionPattern);
    if (companionIdx === -1) {
      throw new Error(
        'YamlRouteTest.kt does not contain "companion object {"',
      );
    }

    const modifiedKotlin =
      kotlinContent.slice(0, companionIdx) +
      testMethodCode +
      kotlinContent.slice(companionIdx);

    // 5. Create blobs for both files
    const [yamlBlob, kotlinBlob] = await Promise.all([
      this._request('POST', `/repos/${this._owner()}/${this._repo()}/git/blobs`, {
        content: yamlContent,
        encoding: 'utf-8',
      }),
      this._request('POST', `/repos/${this._owner()}/${this._repo()}/git/blobs`, {
        content: modifiedKotlin,
        encoding: 'utf-8',
      }),
    ]);

    // 6–7. Create a new tree based on main
    const newTree = await this._request(
      'POST',
      `/repos/${this._owner()}/${this._repo()}/git/trees`,
      {
        base_tree: mainSha,
        tree: [
          {
            path: `${config.yamlDir}/${filename}`,
            mode: '100644',
            type: 'blob',
            sha: yamlBlob.sha,
          },
          {
            path: config.testFile,
            mode: '100644',
            type: 'blob',
            sha: kotlinBlob.sha,
          },
        ],
      },
    );

    // 8. Create commit
    const commit = await this._request(
      'POST',
      `/repos/${this._owner()}/${this._repo()}/git/commits`,
      {
        message: `Add route test: ${routeName}`,
        tree: newTree.sha,
        parents: [mainSha],
      },
    );

    // 9. Update branch ref to point to the new commit
    await this._request(
      'PATCH',
      `/repos/${this._owner()}/${this._repo()}/git/refs/heads/${branchName}`,
      { sha: commit.sha },
    );

    return { branchName };
  }

  async createPR({ routeName, yamlContent, branchName }) {
    const prBody = [
      `## Route Test: ${routeName}`,
      '',
      'This PR was generated by the Route Builder tool.',
      '',
      '### YAML Definition',
      '',
      '```yaml',
      yamlContent,
      '```',
    ].join('\n');

    const pr = await this._request(
      'POST',
      `/repos/${this._owner()}/${this._repo()}/pulls`,
      {
        title: `Add route test: ${routeName}`,
        head: branchName,
        base: 'main',
        body: prBody,
      },
    );

    return { prUrl: pr.html_url };
  }

  async createRoutePR({ routeName, yamlContent, testMethodCode, filename }) {
    const { branchName } = await this.saveToBranch({
      routeName, yamlContent, testMethodCode, filename,
    });
    const { prUrl } = await this.createPR({
      routeName, yamlContent, branchName,
    });
    return { prUrl, branchName };
  }

  async dispatchTestRun({ branchName, testMethodName }) {
    return this._request(
      'POST',
      `/repos/${this._owner()}/${this._repo()}/actions/workflows/${config.github.workflowFile}/dispatches`,
      {
        ref: branchName,
        inputs: {
          test_class: `${config.testClass}#${testMethodName}`,
        },
      },
    );
  }

  async getLatestWorkflowRun(branchName) {
    const data = await this._request(
      'GET',
      `/repos/${this._owner()}/${this._repo()}/actions/runs?branch=${encodeURIComponent(branchName)}&event=workflow_dispatch&per_page=1`,
    );

    if (!data.workflow_runs || data.workflow_runs.length === 0) {
      return null;
    }

    const run = data.workflow_runs[0];
    return {
      status: run.status,
      conclusion: run.conclusion,
      htmlUrl: run.html_url,
    };
  }
}
