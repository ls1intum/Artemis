import http from 'k6/http';
import ws from 'k6/ws';

const defaultXSRFToken = "42d141b5-9e1c-4390-ae06-5143753b4459";
const protocol = "https"; // https or http
const websocketProtocol = "wss"; // wss if https is used; ws if http is used
const host = __ENV.BASE_URL; // host including port if differing from 80 (http) or 443 (https)
const baseUrl = protocol + "://" + host;

const userAgent = 'Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:66.0) Gecko/20100101 Firefox/66.0';
const acceptLanguage = 'en-CA,en-US;q=0.7,en;q=0.3';
const acceptEncoding = 'gzip, deflate, br';

const request = function(method, endpoint, authToken, xsrftoken, body, params) {
    let paramString;
    if (params) {
        paramString = Object.keys(params).map(key => key + '=' + params[key]).join('&');
    }

    let url = baseUrl + '/api' + endpoint + (paramString ? '?' + paramString : '');
    let req = [{
        method: method,
        url: url,
        body: body ? JSON.stringify(body) : null,
        params: {
            'cookies': {
                'XSRF-TOKEN': xsrftoken
            },
            'headers': {
                'Host': host,
                'User-Agent': userAgent,
                'Accept': 'application/json, text/plain, */*',
                'Accept-Language': acceptLanguage,
                'Accept-Encoding': acceptEncoding,
                'Referer': baseUrl + '/',
                'Authorization': 'Bearer ' + authToken,
                'X-XSRF-TOKEN': xsrftoken,
                'Content-Type': 'application/json',
                'Connection': 'keep-alive',
                'TE': 'Trailers'
            },
            'tags': {name: url}
        }
    }];

    return http.batch(req);
};

export function login(username, password) {
    let req, res;

    // The user logs in; the authToken gets saved as we need it later
    req = [{
        'method': 'post',
        'url': baseUrl + '/api/authenticate',
        'body': '{"username":"' + username + '","password":"' + password + '","rememberMe":true}',
        'params': {
            'cookies': {
                'XSRF-TOKEN': defaultXSRFToken
            },
            'headers': {
                'Host': host,
                'User-Agent': userAgent,
                'Accept': 'application/json, text/plain, */*',
                'Accept-Language': acceptLanguage,
                'Accept-Encoding': acceptEncoding,
                'Referer': baseUrl + '/',
                'X-XSRF-TOKEN': defaultXSRFToken,
                'Content-Type': 'application/json',
                'Connection': 'keep-alive',
                'TE': 'Trailers'
            },
            'tags': {'name': baseUrl + '/api/authenticate'}
        }
    }];
    res = http.batch(req);
    const authToken = JSON.parse(res[0].body).id_token;
    console.log('GOT authToken ' + authToken);

    // The user requests it own information of the account
    req = [{
        'method': 'get',
        'url': baseUrl + '/api/account',
        'params': {
            'cookies': {
                'XSRF-TOKEN': defaultXSRFToken
            },
            'headers': {
                'Host': host,
                'User-Agent': userAgent,
                'Accept': 'application/json, text/plain, */*',
                'Accept-Language': acceptLanguage,
                'Accept-Encoding': acceptEncoding,
                'Referer': baseUrl + '/',
                'Authorization': 'Bearer ' + authToken,
                'Connection': 'keep-alive',
                'TE': 'Trailers'
            },
            'tags': {'name': baseUrl + '/api/account'}
        }
    }];
    res = http.batch(req);
    // A new XSRF Token is needed now, we have to extract it from the cookies
    const xsrftoken = res[0].headers['Set-Cookie'].match('(.*XSRF-TOKEN=)([a-z0-9]+[a-z0-9\\-]+[a-z0-9]+)(;.*)')[2];

    return new Artemis(authToken, xsrftoken);
}

export function Artemis(authToken, xsrftoken) {
    this.get = function(endpoint, params) {
        return request('get', endpoint, authToken, xsrftoken, null, params);
    };
    this.post = function(endpoint, body, params) {
        return request('post', endpoint, authToken, xsrftoken, body, params);
    };
    this.put = function(endpoint, body, params) {
        return request('put', endpoint, authToken, xsrftoken, body, params);
    };
    this.delete = function(endpoint, params) {
        return request('delete', endpoint, authToken, xsrftoken, null, params);
    };
    this.websocket = function(doOnSocket) {
        const websocketEndpoint = websocketProtocol + '://' + host + '/websocket/tracker/websocket';
        const websocketUrl = websocketEndpoint + '?access_token=' + authToken;

        ws.connect(websocketUrl, { tags: { name: websocketEndpoint } }, function(socket) {
            socket.on('open', function open() {
                socket.send('CONNECT\nX-XSRF-TOKEN:' + xsrftoken + '\naccept-version:1.1,1.0\nheart-beat:10000,10000\n\n\u0000');
            });

            doOnSocket(socket);
        });
    };
}
