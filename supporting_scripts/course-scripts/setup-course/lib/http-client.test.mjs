import assert from 'node:assert/strict';
import test from 'node:test';
import { createMultipartFormData, HttpClient } from './http-client.mjs';

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

test('forwards the session and upload fields and retains the server error payload', async (t) => {
    const session = new HttpClient('http://127.0.0.1:8080');
    const fetch = t.mock.method(globalThis, 'fetch', async () => new Response('signed in', { headers: { 'Set-Cookie': 'XSRF-TOKEN=a%2Fb; Path=/' } }));
    assert.equal((await session.post('/login', { username: 'local-test' })).data, 'signed in');

    const upload = createMultipartFormData({ title: 'Trial', exercise: { id: 42 }, file: { filename: 'notes.txt', contentType: 'text/plain', content: 'sample' } });
    fetch.mock.mockImplementation(async (_url, options) => {
        assert.equal(options.method, 'POST');
        assert.equal(options.headers.Cookie, 'XSRF-TOKEN=a%2Fb');
        assert.equal(options.headers['X-XSRF-TOKEN'], 'a/b');
        const fields = await new Response(options.body, { headers: options.headers }).formData();
        assert.equal(fields.get('title'), 'Trial');
        assert.deepEqual(JSON.parse(fields.get('exercise')), { id: 42 });
        assert.equal(fields.get('file').name, 'notes.txt');
        assert.equal(await fields.get('file').text(), 'sample');
        return Response.json({ detail: 'Exercise already exists' }, { status: 409 });
    });
    await assert.rejects(session.post('/api/import', upload.body, { headers: { 'Content-Type': upload.contentType } }), (error) => {
        assert.deepEqual(error.response, { status: 409, data: { detail: 'Exercise already exists' } });
        return true;
    });
});
