import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const colors = {
    brightgreen: '#4c1',
    green: '#97ca00',
    yellowgreen: '#a4a61d',
    yellow: '#dfb317',
    orange: '#fe7d37',
    red: '#e05d44',
    blue: '#007ec6',
};

function escapeXml(value) {
    return value.replace(/[&<>"']/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&apos;' })[character]);
}

/** A self-contained SVG: no scripts, network requests, or downloaded fonts. */
export function renderBadge(label, message, color) {
    if (
        ![label, message].every((value) => typeof value === 'string' && value.length > 0 && value.length <= 100 && !/[\u0000-\u001f]/u.test(value)) ||
        !Object.hasOwn(colors, color)
    ) {
        throw new Error('Invalid badge text or color');
    }
    const left = label.length * 7 + 12;
    const right = message.length * 7 + 12;
    const width = left + right;
    return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="20" role="img" aria-label="${escapeXml(`${label}: ${message}`)}">
  <title>${escapeXml(`${label}: ${message}`)}</title>
  <clipPath id="badge"><rect width="${width}" height="20" rx="3"/></clipPath>
  <g clip-path="url(#badge)"><path fill="#555" d="M0 0h${left}v20H0z"/><path fill="${colors[color]}" d="M${left} 0h${right}v20H${left}z"/></g>
  <g fill="#fff" text-anchor="middle" font-family="monospace" font-size="11">
    <text x="${left / 2}" y="14">${escapeXml(label)}</text>
    <text x="${left + right / 2}" y="14">${escapeXml(message)}</text>
  </g>
</svg>
`;
}

/** Only stable Artemis tags, never scoped package releases or development versions. */
export function latestArtemisRelease(releases) {
    if (!Array.isArray(releases)) {
        throw new Error('GitHub releases response is not an array');
    }
    const candidates = releases.filter(
        (release) => release?.draft === false && release?.prerelease === false && /^v?(0|[1-9]\d*)\.(0|[1-9]\d*)(?:\.(0|[1-9]\d*))?$/.test(release.tag_name),
    );
    candidates.sort((a, b) => {
        const parts = (tag) => tag.replace(/^v/, '').split('.').map(BigInt);
        const left = parts(a.tag_name);
        const right = parts(b.tag_name);
        for (let index = 0; index < 3; index += 1) {
            const difference = (right[index] ?? 0n) - (left[index] ?? 0n);
            if (difference !== 0n) return difference > 0n ? 1 : -1;
        }
        return 0;
    });
    if (!candidates.length) throw new Error('No stable Artemis release found');
    return candidates[0].tag_name;
}

/** Read every page: scoped releases must not hide the latest Artemis release. */
export async function fetchReleases(repository, token, request = fetch, apiUrl = 'https://api.github.com') {
    if (!/^[\w.-]+\/[\w.-]+$/.test(repository)) throw new Error('Invalid GitHub repository');
    const releases = [];
    for (let page = 1; page <= 100; page += 1) {
        const response = await request(`${apiUrl}/repos/${repository}/releases?per_page=100&page=${page}`, {
            headers: { Accept: 'application/vnd.github+json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
            signal: AbortSignal.timeout(30_000),
        });
        if (!response.ok) throw new Error(`GitHub releases request failed: HTTP ${response.status}`);
        const batch = await response.json();
        if (!Array.isArray(batch)) throw new Error('GitHub releases response is not an array');
        releases.push(...batch);
        if (batch.length < 100) return releases;
    }
    throw new Error('GitHub releases pagination exceeded the safety limit');
}

export function renderCoverage(file) {
    const coverage = JSON.parse(fs.readFileSync(file, 'utf8'));
    if (coverage.schemaVersion !== 1 || coverage.label !== 'coverage' || !/^\d{1,3}\.\d%$/.test(coverage.message) || Number.parseFloat(coverage.message) > 100) {
        throw new Error('Invalid coverage badge data');
    }
    return renderBadge('coverage', coverage.message, coverage.color);
}

export async function main(args) {
    const [mode, directory] = args;
    if (!['coverage', 'metadata'].includes(mode) || !directory || args.length !== 2) {
        throw new Error('Usage: render-badges.mjs <coverage|metadata> <badges-directory>');
    }
    // Validate all inputs before writing any output. A failed source leaves the last good SVGs.
    const files =
        mode === 'coverage'
            ? { 'coverage.svg': renderCoverage(path.join(directory, 'coverage.json')) }
            : {
                  'latest-version.svg': renderBadge(
                      'latest version',
                      latestArtemisRelease(await fetchReleases(process.env.GITHUB_REPOSITORY, process.env.GITHUB_TOKEN, fetch, process.env.GITHUB_API_URL)),
                      'blue',
                  ),
                  'license.svg': renderBadge('license', 'MIT', 'blue'),
              };
    if (mode === 'metadata' && fs.existsSync(path.join(directory, 'coverage.json'))) {
        files['coverage.svg'] = renderCoverage(path.join(directory, 'coverage.json'));
    }
    for (const [name, svg] of Object.entries(files)) fs.writeFileSync(path.join(directory, name), svg);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    main(process.argv.slice(2)).catch((error) => {
        console.error(error.message);
        process.exitCode = 1;
    });
}
