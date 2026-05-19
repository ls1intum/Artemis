/**
 * Minimal static file server for the prebuilt Artemis Angular bundle.
 *
 * Serves build/resources/main/static/ on :9000, proxies all backend
 * API routes (matching proxy.conf.mjs) to localhost:8080, and upgrades
 * WebSocket connections to ws://127.0.0.1:8080.
 *
 * Requires no external dependencies — Node 22+ built-ins only.
 * Intended for use with start-playwright-stack.sh --static.
 */

import { createServer } from 'node:http';
import { createReadStream, existsSync, statSync } from 'node:fs';
import { join, extname } from 'node:path';
import { request as httpRequest } from 'node:http';
import { connect } from 'node:net';

const PORT = 9000;
const BACKEND_HOST = 'localhost';
const BACKEND_PORT = 8080;
const STATIC_DIR = 'build/resources/main/static';

// Keep in sync with proxy.conf.mjs
const PROXY_PREFIXES = [
    '/api/', '/services/', '/management', '/swagger-resources/',
    '/v3/api-docs/', '/h2-console/', '/auth/', '/health/',
    '/public/', '/.well-known/', '/webauthn/', '/login/webauthn',
    '/saml2/', '/login/saml2/',
];

const MIME = {
    '.html': 'text/html; charset=utf-8',
    '.js': 'application/javascript',
    '.mjs': 'application/javascript',
    '.css': 'text/css',
    '.json': 'application/json',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.svg': 'image/svg+xml',
    '.ico': 'image/x-icon',
    '.woff': 'font/woff',
    '.woff2': 'font/woff2',
    '.ttf': 'font/ttf',
    '.eot': 'application/vnd.ms-fontobject',
    '.webp': 'image/webp',
    '.txt': 'text/plain',
};

function shouldProxy(url) {
    return PROXY_PREFIXES.some(p => {
        const prefix = p.replace(/\/$/, '');
        return url === prefix || url.startsWith(prefix + '/');
    });
}

const server = createServer((req, res) => {
    const url = new URL(req.url ?? '/', `http://localhost:${PORT}`);

    if (shouldProxy(url.pathname)) {
        const options = {
            hostname: BACKEND_HOST,
            port: BACKEND_PORT,
            path: req.url,
            method: req.method,
            headers: { ...req.headers, host: `${BACKEND_HOST}:${BACKEND_PORT}` },
        };
        const proxy = httpRequest(options, (backRes) => {
            res.writeHead(backRes.statusCode ?? 502, backRes.headers);
            backRes.pipe(res, { end: true });
        });
        proxy.on('error', () => {
            if (!res.headersSent) res.writeHead(502);
            res.end('Bad Gateway');
        });
        req.pipe(proxy, { end: true });
        return;
    }

    // Static file serving with SPA fallback
    const fallbackPath = join(STATIC_DIR, 'index.html');
    let filePath = join(STATIC_DIR, url.pathname === '/' ? 'index.html' : url.pathname);
    if (!existsSync(filePath) || statSync(filePath).isDirectory()) {
        if (!existsSync(fallbackPath)) {
            res.writeHead(500);
            res.end('Internal Server Error: build not found — run pnpm run webapp:prod first');
            return;
        }
        filePath = fallbackPath;
    }

    const contentType = MIME[extname(filePath)] ?? 'application/octet-stream';
    const stream = createReadStream(filePath);
    stream.on('error', () => {
        if (!res.headersSent) res.writeHead(500);
        res.end('Internal Server Error');
    });
    res.writeHead(200, { 'Content-Type': contentType });
    stream.pipe(res, { end: true });
});

// WebSocket proxy for /websocket/ paths
server.on('upgrade', (req, socket, head) => {
    if (!(req.url ?? '').startsWith('/websocket/')) {
        socket.destroy();
        return;
    }
    const upstream = connect(BACKEND_PORT, BACKEND_HOST, () => {
        const headers = Object.entries(req.headers)
            .map(([k, v]) => `${k}: ${v}`)
            .join('\r\n');
        upstream.write(`${req.method} ${req.url} HTTP/1.1\r\n${headers}\r\n\r\n`);
        if (head.length) upstream.write(head);
    });
    upstream.pipe(socket);
    socket.pipe(upstream);
    socket.on('error', () => upstream.destroy());
    upstream.on('error', () => socket.destroy());
});

server.listen(PORT, () => {
    console.log(`[static-server] ready at http://localhost:${PORT} → static: ${STATIC_DIR}, backend: ${BACKEND_HOST}:${BACKEND_PORT}`);
});
