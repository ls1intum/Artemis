import { group, sleep } from 'k6';
import http from 'k6/http';
import ws from 'k6/ws';
import { Trend } from 'k6/metrics';

/*****************
 * TODO: Note: this file seems to be outdated
 *****************/

var rest_call_metrics = new Trend('rest_call_metrics');

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
        /*{
          "duration": "15s",
          "target": 10
      },
      {
          "duration": "15s",
          "target": 20
      },
      {
          "duration": "15s",
          "target": 30
      },
      {
          "duration": "15s",
          "target": 40
      },
      {
          "duration": "15s",
          "target": 50
      },
      {
          "duration": "15s",
          "target": 60
      },
      {
          "duration": "15s",
          "target": 70
      },
      {
          "duration": "15s",
          "target": 80
      },
      {
          "duration": "15s",
          "target": 90
      },*/
        {
            duration: '300s',
            target: 100,
        },
    ],
    vus: 70,
};

export default function () {
    let defaultXSRFToken = '42d141b5-9e1c-4390-ae06-5143753b4459';
    let protocol = 'http'; // https or http
    let websocketProtocol = 'ws'; // wss if https is used; ws if http is used
    let host = 'nginx:80'; // host including port if differing from 80 (http) or 443 (https)
    let baseUrl = protocol + '://' + host;

    let maxTestUser = 100; // the userId will be an integer between 1 and this number

    let delayBeforeLogin = 1; // Time in seconds the simulated user needs to enter username/password
    let websocketConnectionTime = 100; // Time in seconds the websocket is kept open, if set to 0 no websocket connection is estahblished

    let userAgent = 'Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:66.0) Gecko/20100101 Firefox/66.0';
    let acceptLanguage = 'en-CA,en-US;q=0.7,en;q=0.3';
    let acceptEncoding = 'gzip, deflate, br';

    let username = __ENV.BASE_USERNAME; // USERID gets replaced with a random number between 1 and maxTestUser
    let password = '__ENV.BASE_PASSWORD'; // USERID gets replaced with a random number between 1 and maxTestUser

    group('Artemis Login', function () {
        let req, res;

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
        var i;
        for (i = 0; i < 5; i++) {
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
            ];
            res = http.batch(req);
            rest_call_metrics.add(res[0].timings.waiting);
        }
        sleep(Math.random() * 30);
        // sleep(5000);
    });
}

export function teardown() {
    console.log(rest_call_metrics.avg);
}
