import http from 'k6/http';
import ws from 'k6/ws';
import { fail } from 'k6';

const protocol = 'https'; // https or http
const websocketProtocol = 'wss'; // wss if https is used; ws if http is used
const host = __ENV.BASE_URL; // host including port if differing from 80 (http) or 443 (https)
const baseUrl = protocol + '://' + host;

const userAgent = 'Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:66.0) Gecko/20100101 Firefox/66.0';
const acceptLanguage = 'en-CA,en-US;q=0.7,en;q=0.3';
const acceptEncoding = 'gzip, deflate, br';

const request = function (method, endpoint, authToken, body, params) {
    let paramString;
    if (params) {
        paramString = Object.keys(params)
            .map((key) => key + '=' + params[key])
            .join('&');
    }

    let url = baseUrl + '/api' + endpoint + (paramString ? '?' + paramString : '');
    let req = [
        {
            method: method,
            url: url,
            body: body ? JSON.stringify(body) : null,
            params: {
                headers: {
                    Host: host,
                    'User-Agent': userAgent,
                    Accept: 'application/json, text/plain, */*',
                    'Accept-Language': acceptLanguage,
                    'Accept-Encoding': acceptEncoding,
                    Referer: baseUrl + '/',
                    'Content-Type': 'application/json',
                    'X-Artemis-Client-Fingerprint': 'b832814fcce0cab9fc5f717d5b93fa07',
                    'X-Artemis-Client-Instance-ID': '9e0b78ec-e43e-43da-a767-89b3f80df63a',
                    Connection: 'keep-alive',
                    TE: 'Trailers',
                },
                tags: { name: url },
                cookies: {
                    jwt: authToken,
                },
            },
        },
    ];

    return http.batch(req);
};

export function login(username, password) {
    let req, res;

    console.log('Try to login with ' + username + ':' + password);

    // The user logs in; the authToken gets saved as we need it later
    req = [
        {
            method: 'post',
            url: baseUrl + '/api/authenticate',
            body: '{"username":"' + username + '","password":"' + password + '","rememberMe":true}',
            params: {
                headers: {
                    Host: host,
                    'User-Agent': userAgent,
                    Accept: 'application/json, text/plain, */*',
                    'Accept-Language': acceptLanguage,
                    'Accept-Encoding': acceptEncoding,
                    Referer: baseUrl + '/',
                    'Content-Type': 'application/json',
                    Connection: 'keep-alive',
                    TE: 'Trailers',
                },
                tags: { name: baseUrl + '/api/authenticate' },
            },
        },
    ];
    res = http.batch(req);
    if (res[0].status !== 200) {
        fail('FAILTEST: failed to login as user ' + username + ' (' + res[0].status + ')! Response was + ' + res[0].body);
    }
    const authToken = res[0].cookies.jwt[0].value;
    // console.log('GOT authToken ' + authToken + ' for user ' + username);

    // The user requests it own information of the account
    req = [
        {
            method: 'get',
            url: baseUrl + '/api/account',
            params: {
                headers: {
                    Host: host,
                    'User-Agent': userAgent,
                    Accept: 'application/json, text/plain, */*',
                    'Accept-Language': acceptLanguage,
                    'Accept-Encoding': acceptEncoding,
                    Referer: baseUrl + '/',
                    Connection: 'keep-alive',
                    TE: 'Trailers',
                },
                tags: { name: baseUrl + '/api/account' },
                cookies: {
                    jwt: authToken,
                },
            },
        },
    ];
    res = http.batch(req);

    return new Artemis(authToken);
}

export function Artemis(authToken) {
    this.get = function (endpoint, params) {
        return request('get', endpoint, authToken, null, params);
    };
    this.post = function (endpoint, body, params) {
        return request('post', endpoint, authToken, body, params);
    };
    this.put = function (endpoint, body, params) {
        return request('put', endpoint, authToken, body, params);
    };
    this.patch = function (endpoint, body, params) {
        return request('patch', endpoint, authToken, body, params);
    };
    this.delete = function (endpoint, params) {
        return request('delete', endpoint, authToken, null, params);
    };
    this.websocket = function (doOnSocket) {
        const websocketEndpoint = websocketProtocol + '://' + host + '/websocket/tracker/websocket';

        const jar = new http.CookieJar();
        jar.set(baseUrl, 'jwt', authToken);

        ws.connect(websocketEndpoint, { tags: { name: websocketEndpoint }, jar }, function (socket) {
            socket.on('open', function open() {
                socket.send('CONNECT\naccept-version:1.2\nheart-beat:10000,10000\n\n\u0000');
                socket.setInterval(function timeout() {
                    socket.ping();
                    // Pinging every 10sec (setInterval)
                }, 10000);
                // TODO: is ping not the same as the heartbeat?
                // Send heartbeat to server so session is kept alive
                socket.setInterval(function timeout() {
                    socket.send('\n');
                }, 10000);
            });

            socket.on('error', function (e) {
                if (e.error() !== 'websocket: close sent') {
                    console.log('Websocket connection closed due to: ', e.error());
                }
                // TODO: try to reconnect
            });

            doOnSocket(socket);
        });
    };
}
