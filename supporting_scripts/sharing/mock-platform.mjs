import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { join } from 'node:path';
import { unzipSync } from 'fflate';

const artemis = 'http://localhost:18280';
const platform = 'http://localhost:18281';
const api = '/api/pluginIF/v0.1';
const basketToken = 'local-basket';
const transferPath = '/sharing/import';
const fixtures = new URL('../../src/test/resources/test-data/', import.meta.url);

function escapeHtml(value) {
    return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
}

function localArtemisUrl(value) {
    if (!URL.canParse(value)) return;
    const url = new URL(value);
    if (url.origin === artemis && !url.username && !url.password) return url;
}

function pollConfig(key, fetchArtemis) {
    const config = new URL('/api/core/sharing/config', artemis);
    config.searchParams.set('apiBaseUrl', platform + api);
    config.searchParams.set('installationName', 'Local CodeAbility QA');
    return fetchArtemis(config, { headers: { Authorization: 'Bearer ' + key }, redirect: 'error', signal: AbortSignal.timeout(30000) });
}

function inspectArchive(bytes) {
    const files = unzipSync(bytes);
    const names = Object.keys(files);
    const detailsName = names.find((name) => /^Exercise-Details-.*\.json$/.test(name));
    const details = JSON.parse(new TextDecoder().decode(files[detailsName]));
    if (details.type !== 'programming' || !details.title) throw new Error('Missing programming exercise details');
    const repositories = names
        .filter((name) => name.endsWith('.zip'))
        .flatMap((name) => {
            const bundle = unzipSync(files[name]);
            return Object.entries(bundle)
                .filter(([entry]) => entry.endsWith('.zip'))
                .map(([entry, contents]) => {
                    const repository = unzipSync(contents);
                    if (!Object.keys(repository).some((file) => !file.endsWith('/'))) throw new Error('Empty exported repository');
                    return entry;
                });
        });
    if (!repositories.length) throw new Error('Missing exported repositories');
    return { title: details.title, entries: names, repositories, sha256: createHash('sha256').update(bytes).digest('hex') };
}

function page(heading, body) {
    return `<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${heading}</title><main><h1>${heading}</h1>${body}</main></html>`;
}

export async function createMockPlatform({ key, output, fetchArtemis = fetch }) {
    if (!key || !output) throw new Error('Provide SHARING_API_KEY and an output directory');
    await mkdir(output, { recursive: true });
    const primary = 'sharing-platform/basket/';
    const [basketFile, secondBasketFile, ...archives] = await Promise.all(
        [primary + 'sampleBasket.json', 'sharing-platform/basket.json', primary + 'sampleExercise.zip', 'import-from-file/valid-import-dto-details.zip'].map((name) =>
            readFile(new URL(name, fixtures)),
        ),
    );
    const basket = JSON.parse(basketFile);
    const [second] = JSON.parse(secondBasketFile).exerciseInfo;
    second.metadata.title = inspectArchive(archives[1]).title;
    second.project.project_name = second.metadata.title;
    basket.exerciseInfo.push(second);
    basket.tokenValidUntil = Date.now() + 24 * 60 * 60 * 1000;
    const apiBaseURL = platform + api;
    const returnURL = platform + '/';
    const checksum = createHash('md5')
        .update(key + apiBaseURL + returnURL)
        .digest('hex');
    const info = { basketToken, apiBaseURL, returnURL, checksum };
    const basketLink = new URL(transferPath + '/' + basketToken, artemis);
    basketLink.search = new URLSearchParams({ apiBaseURL, returnURL, checksum }).toString();

    return createServer(async (req, res) => {
        const url = new URL(req.url, platform);
        const path = url.pathname;
        const send = (status, body, type = 'text/plain; charset=utf-8') => {
            res.writeHead(status, {
                'content-type': type,
                'cache-control': 'no-store',
                'referrer-policy': 'no-referrer',
                'content-security-policy': "default-src 'none'; frame-ancestors 'none'; base-uri 'none'",
            }).end(body);
        };
        const json = (value) => send(200, JSON.stringify(value), 'application/json');
        // Log operation paths only. The reinitialization and export queries carry credentials.
        res.on('finish', () => console.log(new Date().toISOString(), req.method, path, res.statusCode));
        try {
            if (req.method === 'GET' && path === '/') {
                return send(200, page('Local CodeAbility Sharing Platform', `<a href="${escapeHtml(basketLink.href)}">Import basket into Artemis</a>`), 'text/html; charset=utf-8');
            }
            if (req.method === 'GET' && path === '/sharing-info.json') return json(info);
            if ((req.method === 'POST' && path === '/connect') || (req.method === 'GET' && path === api + '/reInitialize')) {
                if (path !== '/connect' && url.searchParams.get('apiKey') !== key) return send(401, 'Incorrect API key');
                const response = await pollConfig(key, fetchArtemis);
                if (!response.ok) return send(502, 'Artemis config returned HTTP ' + response.status);
                const config = await response.json();
                if (config.version !== '0.1' || !config.actions?.some((action) => action.actionTransferURL === transferPath)) {
                    return send(502, 'Artemis did not advertise the basket import action');
                }
                return json(path === '/connect' ? config : true);
            }
            if (req.method !== 'GET') return send(405, 'Method not allowed');
            if (path === api + '/basket/' + basketToken) return Date.now() < basket.tokenValidUntil ? json(basket) : send(200, '', 'application/json');
            if (path.startsWith(api + '/basket/') && !path.slice((api + '/basket/').length).includes('/')) return send(200, '', 'application/json');
            const repositoryPrefix = api + '/basket/' + basketToken + '/repository/';
            if (path.startsWith(repositoryPrefix)) {
                const index = path.slice(repositoryPrefix.length);
                if (url.searchParams.get('format') !== 'artemis' || !['0', '1'].includes(index) || Date.now() >= basket.tokenValidUntil) return send(422, 'Unavailable repository');
                return send(200, archives[Number(index)], 'application/octet-stream');
            }
            if (path === api + '/exercise/import') {
                const exerciseUrl = localArtemisUrl(url.searchParams.get('exerciseUrl'));
                const token = exerciseUrl?.pathname.match(/^\/api\/programming\/sharing\/export\/([\w-]+)$/)?.[1];
                const secret = exerciseUrl?.searchParams.get('sec');
                if (!token || !secret) return send(400, 'Expected a signed export URL on the local Artemis instance');
                const download = new URL(artemis);
                download.pathname = '/api/programming/sharing/export/' + encodeURIComponent(token);
                download.searchParams.set('sec', secret);
                const response = await fetchArtemis(download, { redirect: 'error', signal: AbortSignal.timeout(30000) });
                if (!response.ok) return send(502, 'Artemis export returned HTTP ' + response.status);
                const bytes = Buffer.from(await response.arrayBuffer());
                const receipt = inspectArchive(bytes);
                await writeFile(join(output, 'export-' + receipt.sha256 + '.zip'), bytes, { mode: 0o600 });
                const callback = localArtemisUrl(url.searchParams.get('callBack'));
                const back = callback ? `<p><a href="${escapeHtml(callback.href)}">Return to Artemis</a></p>` : '';
                return send(200, page('Export received', '<pre>' + escapeHtml(JSON.stringify(receipt, null, 2)) + '</pre>' + back), 'text/html; charset=utf-8');
            }
            send(404, 'Unknown mock platform endpoint');
        } catch (error) {
            console.error('Platform operation failed:', error.name);
            send(502, 'Could not complete the local Artemis exchange');
        }
    });
}

if (import.meta.main) {
    const server = await createMockPlatform({ key: process.env.SHARING_API_KEY, output: process.argv[2] });
    server.listen(18281, '127.0.0.1', () => console.log('CodeAbility mock listening at ' + platform));
}
