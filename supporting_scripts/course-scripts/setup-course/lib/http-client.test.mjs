import assert from 'node:assert/strict';
import test from 'node:test';
import { HttpClient } from './http-client.mjs';

const headers = { 'Content-Type': 'application/json' };
const client = new HttpClient('http://127.0.0.1:8080');

test('propagates a failure while reading a JSON response body', async (t) => {
    const controller = new AbortController();
    const failure = new DOMException('The response timed out', 'TimeoutError');
    const body = new ReadableStream({
        pull(stream) {
            controller.abort(failure);
            stream.error(controller.signal.reason);
        },
    });
    t.mock.method(globalThis, 'fetch', async (_url, options) => {
        assert.equal(options.signal, controller.signal);
        return new Response(body, { headers });
    });
    await assert.rejects(client.get('/api/test', { signal: controller.signal }), (error) => error === failure);
});

test('parses valid JSON and preserves the fallback for malformed JSON', async (t) => {
    const fetch = t.mock.method(globalThis, 'fetch', async () => new Response('{"id":42}', { headers }));
    assert.deepEqual((await client.get('/api/test')).data, { id: 42 });
    fetch.mock.mockImplementation(async () => new Response('{', { headers }));
    assert.equal((await client.get('/api/test')).data, null);
});
