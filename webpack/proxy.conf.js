function setupProxy({ tls }) {
  return [
    {
      context: [
        '/api',
        '/services',
        '/management',
        '/swagger-resources',
        '/v3/api-docs',
        '/h2-console',
        '/auth',
        '/health',
        '/time',
        '/public'
      ],
      target: `http${tls ? 's' : ''}://localhost:8080`,
      secure: false,
      changeOrigin: tls,
    },
    {
      context: ['/websocket'],
      target: 'ws://127.0.0.1:8080',
      ws: true,
    },
  ];
}

module.exports = setupProxy;
