import http from 'node:http';

const port = Number(process.env.HYPERION_LLM_MOCK_PORT ?? 1234);
let requestCount = 0;

function jsonResponse(res, status, body) {
    res.writeHead(status, { 'content-type': 'application/json' });
    res.end(JSON.stringify(body));
}

function toolCallResponse(toolName, args) {
    requestCount++;
    return {
        id: `chatcmpl-hyperion-e2e-${requestCount}`,
        object: 'chat.completion',
        created: Math.floor(Date.now() / 1000),
        model: 'hyperion-e2e-mock',
        choices: [
            {
                index: 0,
                message: {
                    role: 'assistant',
                    content: '',
                    tool_calls: [
                        {
                            id: `call_hyperion_e2e_${requestCount}`,
                            type: 'function',
                            function: {
                                name: toolName,
                                arguments: JSON.stringify(args),
                            },
                        },
                    ],
                },
                finish_reason: 'tool_calls',
            },
        ],
        usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2 },
    };
}

const server = http.createServer((req, res) => {
    if (req.method === 'GET' && req.url === '/health') {
        jsonResponse(res, 200, { ok: true, requestCount });
        return;
    }

    if (req.method === 'POST' && req.url === '/v1/chat/completions') {
        req.resume();
        req.on('end', () => {
            jsonResponse(res, 200, toolCallResponse('bash', { command: 'sleep 30' }));
        });
        return;
    }

    jsonResponse(res, 404, { error: `Unhandled ${req.method} ${req.url}` });
});

server.listen(port, '127.0.0.1', () => {
    console.log(`Hyperion E2E LLM mock listening on http://127.0.0.1:${port}`);
});

process.on('SIGTERM', () => server.close(() => process.exit(0)));
process.on('SIGINT', () => server.close(() => process.exit(0)));
