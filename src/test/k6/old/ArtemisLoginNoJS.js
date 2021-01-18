import { group, sleep } from 'k6';
import http from 'k6/http';
import ws from 'k6/ws';

/*****************
 * TODO: Note: this file seems to be outdated
 *****************/

export let options = {
    maxRedirects: 0,
    ext: {
        loadimpact: {
            distribution: {
                load_zone0: {
                    loadZone: 'amazon:de:frankfurt',
                    percent: 100,
                },
            },
        },
    },
    stages: [
        {
            duration: '5s',
            target: 30,
        },
        {
            duration: '5s',
            target: 50,
        },
        {
            duration: '5s',
            target: 80,
        },
        {
            duration: '300s',
            target: 100,
        },
    ],
    vus: 100,
};

export default function () {
    let defaultXSRFToken = '42d141b5-9e1c-4390-ae06-5143753b4459';
    let protocol = 'https'; // https or http
    let websocketProtocol = 'wss'; // wss if https is used; ws if http is used
    let host = 'artemis.ase.in.tum.de'; // host including port if differing from 80 (http) or 443 (https)
    let baseUrl = protocol + '://' + host;

    let maxTestUser = 100; // the userId will be an integer between 1 and this number

    let delayBeforeLogin = 1; // Time in seconds the simulated user needs to enter username/password
    let websocketConnectionTime = 300; // Time in seconds the websocket is kept open, if set to 0 no websocket connection is established

    let userAgent = 'Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:66.0) Gecko/20100101 Firefox/66.0';
    let acceptLanguage = 'en-CA,en-US;q=0.7,en;q=0.3';
    let acceptEncoding = 'gzip, deflate, br';

    let username = __ENV.BASE_USERNAME; // USERID gets replaced with a random number between 1 and maxTestUser
    let password = '__ENV.BASE_PASSWORD'; // USERID gets replaced with a random number between 1 and maxTestUser

    group('Artemis Login', function () {
        let req, res;

        // The website is loaded initialy
        req = [
            {
                method: 'get',
                url: baseUrl + '/',
                params: {
                    headers: {
                        Host: host,
                        'User-Agent': userAgent,
                        Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
                        'Accept-Language': acceptLanguage,
                        'Accept-Encoding': acceptEncoding,
                        Connection: 'keep-alive',
                        'Upgrade-Insecure-Requests': '1',
                        Pragma: 'no-cache',
                        'Cache-Control': 'no-cache',
                    },
                    tags: { name: baseUrl + '/' },
                },
            },
        ];
        res = http.batch(req);

        // The favicon, the translations, some management information and the TUM logo is downloaded next
        req = [
            {
                method: 'get',
                url: baseUrl + '/i18n/en.json?buildTimestamp=1556135702468',
                params: {
                    cookies: {
                        'XSRF-TOKEN': defaultXSRFToken,
                    },
                    headers: {
                        Host: host,
                        'User-Agent': userAgent,
                        Accept: 'application/json, text/plain, */*',
                        'Accept-Language': acceptLanguage,
                        'Accept-Encoding': acceptEncoding,
                        Referer: baseUrl + '/',
                        Connection: 'keep-alive',
                        Pragma: 'no-cache',
                        'Cache-Control': 'no-cache',
                        TE: 'Trailers',
                    },
                    tags: { name: baseUrl + '/i18n' },
                },
            },
            {
                method: 'get',
                url: baseUrl + '/management/info',
                params: {
                    cookies: {
                        'XSRF-TOKEN': defaultXSRFToken,
                    },
                    headers: {
                        Host: host,
                        'User-Agent': userAgent,
                        Accept: 'application/json, text/plain, */*',
                        'Accept-Language': acceptLanguage,
                        'Accept-Encoding': acceptEncoding,
                        Referer: baseUrl + '/',
                        Connection: 'keep-alive',
                        Pragma: 'no-cache',
                        'Cache-Control': 'no-cache',
                        TE: 'Trailers',
                    },
                    tags: { name: baseUrl + '/management/info' },
                },
            },
            {
                method: 'get',
                url: baseUrl + '/api/account',
                params: {
                    cookies: {
                        'XSRF-TOKEN': defaultXSRFToken,
                    },
                    headers: {
                        Host: host,
                        'User-Agent': userAgent,
                        Accept: 'application/json, text/plain, */*',
                        'Accept-Language': acceptLanguage,
                        'Accept-Encoding': acceptEncoding,
                        Referer: baseUrl + '/',
                        Connection: 'keep-alive',
                        Pragma: 'no-cache',
                        'Cache-Control': 'no-cache',
                        TE: 'Trailers',
                    },
                    tags: { name: baseUrl + '/api/account' },
                },
            },
        ];
        res = http.batch(req);

        // Now the user should type his login credentials, therefor we wait some time
        sleep(delayBeforeLogin);

        // The user is randomly selected
        let userId = Math.floor(Math.random() * maxTestUser) + 1;
        let currentUsername = username.replace('USERID', userId);
        let currentPassword = password.replace('USERID', userId);

        // The user logs in; the authToken gets saved as we need it later
        req = [
            {
                method: 'post',
                url: baseUrl + '/api/authenticate',
                body: '{"username":"' + currentUsername + '","password":"' + currentPassword + '","rememberMe":true}',
                params: {
                    cookies: {
                        'XSRF-TOKEN': defaultXSRFToken,
                    },
                    headers: {
                        Host: host,
                        'User-Agent': userAgent,
                        Accept: 'application/json, text/plain, */*',
                        'Accept-Language': acceptLanguage,
                        'Accept-Encoding': acceptEncoding,
                        Referer: baseUrl + '/',
                        'X-XSRF-TOKEN': defaultXSRFToken,
                        'Content-Type': 'application/json',
                        'Content-Length': '74',
                        Connection: 'keep-alive',
                        TE: 'Trailers',
                    },
                    tags: { name: baseUrl + '/api/authenticate' },
                },
            },
        ];
        res = http.batch(req);
        let authToken = JSON.parse(res[0].body).id_token;

        // The user requests it own information of the account
        req = [
            {
                method: 'get',
                url: baseUrl + '/api/account',
                params: {
                    cookies: {
                        'XSRF-TOKEN': defaultXSRFToken,
                    },
                    headers: {
                        Host: host,
                        'User-Agent': userAgent,
                        Accept: 'application/json, text/plain, */*',
                        'Accept-Language': acceptLanguage,
                        'Accept-Encoding': acceptEncoding,
                        Referer: baseUrl + '/',
                        Authorization: 'Bearer ' + authToken,
                        Connection: 'keep-alive',
                        TE: 'Trailers',
                    },
                    tags: { name: baseUrl + '/api/account' },
                },
            },
        ];

        res = http.batch(req);

        // A new XSRF Token is needed now, we have to extract it from the cookies
        let xsrftoken = res[0].headers['Set-Cookie'].match('XSRF-TOKEN=(.*); path=/(; secure)?')[1];

        // Extract user as we need it for some websocket information
        let user = JSON.parse(res[0].body);

        // Some more calls (dashboard, notification, courses to register) are made
        req = [
            {
                method: 'get',
                url: baseUrl + '/api/courses/for-dashboard',
                params: {
                    cookies: {
                        'XSRF-TOKEN': xsrftoken,
                    },
                    headers: {
                        Host: host,
                        'User-Agent': userAgent,
                        Accept: 'application/json, text/plain, */*',
                        'Accept-Language': acceptLanguage,
                        'Accept-Encoding': acceptEncoding,
                        Referer: baseUrl + '/',
                        Authorization: 'Bearer ' + authToken,
                        Connection: 'keep-alive',
                        TE: 'Trailers',
                    },
                    tags: { name: baseUrl + '/api/courses/for-dashboard' },
                },
            },
            {
                method: 'get',
                url: baseUrl + '/api/notifications/for-user',
                params: {
                    cookies: {
                        'XSRF-TOKEN': xsrftoken,
                    },
                    headers: {
                        Host: host,
                        'User-Agent': userAgent,
                        Accept: 'application/json, text/plain, */*',
                        'Accept-Language': acceptLanguage,
                        'Accept-Encoding': acceptEncoding,
                        Referer: baseUrl + '/',
                        Authorization: 'Bearer ' + authToken,
                        Connection: 'keep-alive',
                        TE: 'Trailers',
                    },
                    tags: { name: baseUrl + '/api/notifications/for-user' },
                },
            },
        ];
        res = http.batch(req);

        let courses = JSON.parse(res[1].body);

        // Initiate websocket connection if connection time is set to value greater than 0
        if (websocketConnectionTime > 0) {
            let websocketEndpoint = websocketProtocol + '://' + host + '/websocket/tracker/websocket';
            let websocketUrl = websocketEndpoint + '?access_token=' + authToken;

            ws.connect(websocketUrl, { tags: { name: websocketEndpoint } }, function (socket) {
                socket.on('open', function open() {
                    socket.send('CONNECT\nX-XSRF-TOKEN:' + xsrftoken + '\naccept-version:1.2\nheart-beat:10000,10000\n\n\u0000');
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
                });

                function getSubscriptionId() {
                    return Math.random()
                        .toString(36)
                        .replace(/[^a-z]+/g, '')
                        .substr(0, 12);
                }

                function subscribeCourse(courseId, role) {
                    socket.send('SUBSCRIBE\nid:sub-' + getSubscriptionId() + '\ndestination:/topic/course/' + courseId + '/' + role + '\n\n\u0000');
                }

                // Send destination and subscription after 1 second
                socket.setTimeout(function () {
                    socket.send('SEND\ndestination:/topic/activity\ncontent-length:20\n\n{"page":"/overview"}\u0000');

                    courses.forEach(function (course) {
                        if (user.groups.includes(course.studentGroupName)) {
                            subscribeCourse(course.id, 'STUDENT');
                        }
                        if (user.groups.includes(course.teachingAssistantGroupName)) {
                            subscribeCourse(course.id, 'TA');
                        }
                        if (user.groups.includes(course.instructorGroupName)) {
                            subscribeCourse(course.id, 'INSTRUCTOR');
                        }

                        // Subscribe for system and user notifications
                        socket.send('SUBSCRIBE\nid:sub-' + getSubscriptionId() + '\ndestination:/topic/system-notification\n\n\u0000');
                        socket.send('SUBSCRIBE\nid:sub-' + getSubscriptionId() + '\ndestination:/topic/user/' + user.id + '/notifications\n\n\u0000');
                    });
                }, 1 * 1000);

                socket.setTimeout(function () {
                    socket.close();
                }, websocketConnectionTime * 1000);
            });
        }
    });
}
