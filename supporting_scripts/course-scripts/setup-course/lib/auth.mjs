/**
 * Authentication utilities
 */

/**
 * Check if the server is running and accessible
 * @param {HttpClient} client - The HTTP client instance
 * @returns {Promise<boolean>} True if server is accessible
 */
export async function checkServerConnection(client) {
    try {
        // Try to access a public endpoint to check server connectivity
        await client.get('/api/core/public/account');
        return true;
    } catch (error) {
        // 401 is expected - it means server is running but user not authenticated
        if (error.response?.status === 401) {
            return true;
        }
        // Check if this is a connection error
        if (error.cause?.code === 'ECONNREFUSED' || error.message?.includes('ECONNREFUSED')) {
            return false;
        }
        // Any other status code means server is running
        if (error.response?.status) {
            return true;
        }
        return false;
    }
}

/**
 * Authenticate a user with the Artemis server
 * @param {HttpClient} client - The HTTP client instance
 * @param {string} username - The username
 * @param {string} password - The password
 * @param {boolean} silent - If true, don't log success message
 * @returns {Promise<object>} The authentication response data
 */
export async function authenticate(client, username, password, silent = false) {
    // First, check if server is accessible and get CSRF token
    try {
        await client.get('/api/core/public/account');
    } catch (error) {
        // 401 is expected when not authenticated, we just need the CSRF cookie
        // But connection errors should be reported
        if (error.cause?.code === 'ECONNREFUSED' || error.message?.includes('ECONNREFUSED')) {
            throw new Error(
                `Cannot connect to Artemis server at ${client.baseUrl}. ` +
                    `Make sure the server is running (./gradlew bootRun)`
            );
        }
        // Other errors during CSRF fetch are okay
    }

    try {
        const response = await client.post('/api/core/public/authenticate', {
            username,
            password,
            rememberMe: true,
        });

        if (response.status !== 200) {
            throw new Error(`Authentication failed for user ${username}`);
        }

        if (!silent) {
            console.log(`  Authenticated as ${username}`);
        }
        return response.data;
    } catch (error) {
        // Check for common error cases
        if (error.response?.status === 401) {
            throw new Error(`Authentication failed for user ${username}: Invalid credentials`);
        }
        if (error.response?.status === 404) {
            throw new Error(
                `Authentication endpoint not found at ${client.baseUrl}. ` +
                    `This may indicate the Artemis server is not running or the URL is incorrect. ` +
                    `Make sure the server is running with: ./gradlew bootRun`
            );
        }
        throw error;
    }
}
