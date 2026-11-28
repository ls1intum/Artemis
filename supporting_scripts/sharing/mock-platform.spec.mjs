import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { once } from 'node:events';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';

import { createMockPlatform } from './mock-platform.mjs';

const api = '/api/pluginIF/v0.1';
const key = 'fixture-key';
const archive = await readFile(new URL('../../src/test/resources/test-data/sharing-platform/basket/sampleExercise.zip', import.meta.url));

async function platform(t, fetchArtemis) {
    const output = await mkdtemp(join(tmpdir(), 'sharing-mock-test-'));
    const server = await createMockPlatform({ key, output, fetchArtemis });
    server.listen(0, '127.0.0.1');
    await once(server, 'listening');
    t.after(async () => {
        await new Promise((resolve) => server.close(resolve));
        await rm(output, { recursive: true, force: true });
    });
    return { request: (path, opts) => fetch(`http://127.0.0.1:${server.address().port}${path}`, opts), output };
}

test('returns the signed basket and the corresponding archive for each zero-based position', async (t) => {
    const { request } = await platform(t);
    const info = await (await request('/sharing-info.json')).json();
    assert.equal(
        info.checksum,
        createHash('md5')
            .update(key + info.apiBaseURL + info.returnURL)
            .digest('hex'),
    );
    assert.match(await (await request('/')).text(), /\/sharing\/import\/local-basket\?/);
    const basket = await (await request(`${api}/basket/${info.basketToken}`)).json();
    assert.match(basket.exerciseInfo[0].metadata.title, /JUnit IO Tests/);
    assert.ok(basket.tokenValidUntil > Date.now());
    assert.deepEqual(Buffer.from(await (await request(`${api}/basket/local-basket/repository/0?format=artemis`)).arrayBuffer()), archive);
    assert.equal((await request(`${api}/basket/absent`)).status, 200);
    assert.equal(await (await request(`${api}/basket/absent`)).text(), '');
    assert.equal(basket.exerciseInfo[1].metadata.title, 'dtoShapedImport');
    const secondArchive = await readFile(new URL('../../src/test/resources/test-data/import-from-file/valid-import-dto-details.zip', import.meta.url));
    assert.deepEqual(Buffer.from(await (await request(`${api}/basket/local-basket/repository/1?format=artemis`)).arrayBuffer()), secondArchive);
    assert.equal((await request(`${api}/basket/local-basket/repository/2?format=artemis`)).status, 422);
});

test('authenticates reinitialization and registers the local callback API', async (t) => {
    const calls = [];
    const { request } = await platform(t, (url, options) => {
        calls.push([url, options]);
        return Response.json({ version: '0.1', pluginName: 'Artemis Sharing Connector', actions: [{ actionId: 'Import', actionTransferURL: '/sharing/import' }] });
    });
    assert.equal((await request(`${api}/reInitialize?apiKey=incorrect`)).status, 401);
    assert.deepEqual(calls, []);
    assert.equal(await (await request(`${api}/reInitialize?apiKey=${key}`)).json(), true);
    const [[url, options]] = calls;
    assert.equal(url.origin + url.pathname, 'http://localhost:18280/api/core/sharing/config');
    assert.equal(url.searchParams.get('apiBaseUrl'), 'http://localhost:18281' + api);
    assert.equal(options.headers.Authorization, 'Bearer ' + key);
    assert.equal(options.redirect, 'error');
});

test('verifies and saves an Artemis export and rejects invalid archives or other destinations', async (t) => {
    let calls = 0;
    const { request, output } = await platform(t, (url, options) => {
        assert.equal(url.origin, 'http://localhost:18280');
        assert.equal(options.redirect, 'error');
        return new Response(calls++ === 0 ? archive : Buffer.from('invalid archive'));
    });
    const endpoint = api + '/exercise/import?exerciseUrl=';
    const callback = 'http://localhost:18280/api/programming/sharing/export/example?sec=signature';
    for (const url of [
        callback.replace('18280', '18282'),
        callback.replace('/api/programming/sharing/export/example', '/api/account'),
        callback.replace('localhost', 'user:pw@localhost'),
    ]) {
        assert.equal((await request(endpoint + encodeURIComponent(url))).status, 400);
    }
    assert.equal(calls, 0);
    const result = await request(endpoint + encodeURIComponent(callback));
    assert.equal(result.status, 200);
    const html = await result.text();
    assert.match(html, /JUnit IO Tests/);
    assert.match(html, /TES-JUnit_IO_Tests-tests.zip/);
    const digest = createHash('sha256').update(archive).digest('hex');
    assert.deepEqual(await readFile(join(output, `export-${digest}.zip`)), archive);
    assert.equal((await request(endpoint + encodeURIComponent(callback))).status, 502);
});
