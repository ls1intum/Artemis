/**
 * HTTP client for Artemis API
 */

export class HttpClient {
    constructor(baseUrl) {
        this.baseUrl = baseUrl.replace(/\/$/, '');
        this.cookies = [];
        this.csrfToken = null;
    }

    async request(method, path, options = {}) {
        const url = `${this.baseUrl}${path}`;
        const isApiRequest = path.startsWith('/api');
        const headers = {
            // Only require JSON for API requests
            'Accept': isApiRequest ? 'application/json' : '*/*',
            ...options.headers,
        };

        if (this.cookies.length > 0) {
            headers['Cookie'] = this.cookies.join('; ');
        }

        if (this.csrfToken) {
            headers['X-XSRF-TOKEN'] = this.csrfToken;
        }

        const fetchOptions = {
            method,
            headers,
            redirect: 'manual',
        };

        if (options.body) {
            if (options.contentType === 'multipart') {
                // For multipart, let fetch set the boundary
                fetchOptions.body = options.body;
            } else if (typeof options.body === 'object') {
                headers['Content-Type'] = 'application/json';
                fetchOptions.body = JSON.stringify(options.body);
            } else {
                fetchOptions.body = options.body;
            }
        }

        const response = await fetch(url, fetchOptions);

        // Store cookies from response
        const setCookies = response.headers.getSetCookie?.() || [];
        for (const cookie of setCookies) {
            const cookiePart = cookie.split(';')[0];
            const [name] = cookiePart.split('=');

            // Update or add cookie
            this.cookies = this.cookies.filter(c => !c.startsWith(`${name}=`));
            this.cookies.push(cookiePart);

            // Extract CSRF token
            if (name === 'XSRF-TOKEN') {
                this.csrfToken = decodeURIComponent(cookiePart.split('=')[1]);
            }
        }

        // Parse response body
        let data = null;
        const contentType = response.headers.get('content-type');
        if (contentType?.includes('application/json')) {
            try {
                data = await response.json();
            } catch {
                data = null;
            }
        } else {
            data = await response.text();
        }

        if (!response.ok && response.status !== 201) {
            const error = new Error(`HTTP ${response.status}: ${response.statusText}`);
            error.response = { status: response.status, data };
            throw error;
        }

        return { status: response.status, data, headers: response.headers };
    }

    async get(path, options) {
        return this.request('GET', path, options);
    }

    async post(path, body, options = {}) {
        return this.request('POST', path, { ...options, body });
    }

    async put(path, body, options = {}) {
        return this.request('PUT', path, { ...options, body });
    }

    async patch(path, body, options = {}) {
        return this.request('PATCH', path, { ...options, body });
    }

    async delete(path, options) {
        return this.request('DELETE', path, options);
    }
}

/**
 * Create multipart form data for file uploads
 */
export function createMultipartFormData(fields) {
    const boundary = '----FormBoundary' + Math.random().toString(36).substring(2);
    let body = '';

    for (const [name, value] of Object.entries(fields)) {
        body += `--${boundary}\r\n`;
        if (typeof value === 'object' && value.filename) {
            body += `Content-Disposition: form-data; name="${name}"; filename="${value.filename}"\r\n`;
            body += `Content-Type: ${value.contentType || 'application/octet-stream'}\r\n\r\n`;
            body += value.content + '\r\n';
        } else if (typeof value === 'object') {
            body += `Content-Disposition: form-data; name="${name}"\r\n`;
            body += `Content-Type: application/json\r\n\r\n`;
            body += JSON.stringify(value) + '\r\n';
        } else {
            body += `Content-Disposition: form-data; name="${name}"\r\n\r\n`;
            body += value + '\r\n';
        }
    }
    body += `--${boundary}--\r\n`;

    return {
        body,
        contentType: `multipart/form-data; boundary=${boundary}`,
    };
}
