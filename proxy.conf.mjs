
export default [
    {
        context: [
            "/api",
            "/services",
            "/management",
            "/swagger-resources",
            "/v3/api-docs",
            "/h2-console",
            "/auth",
            "/health",
            "/public",
            "/.well-known"
        ],
        target: `http://localhost:8080`,
        secure: false
    },
    {
        context: ["/websocket"],
        target: "ws://127.0.0.1:8080",
        ws: true
    }
];
