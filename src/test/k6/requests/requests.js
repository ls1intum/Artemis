import http from 'k6/http';
import ws from 'k6/ws';
import { fail } from 'k6';
import { COMMIT } from "./endpoints.js";

const defaultXSRFToken = "42d141b5-9e1c-4390-ae06-5143753b4459";
const protocol = "https"; // https or http
const websocketProtocol = "wss"; // wss if https is used; ws if http is used
const host = __ENV.BASE_URL; // host including port if differing from 80 (http) or 443 (https)
const baseUrl = protocol + "://" + host;

const userAgent = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:66.0) Gecko/20100101 Firefox/66.0";
const acceptLanguage = "en-CA,en-US;q=0.7,en;q=0.3";
const acceptEncoding = "gzip, deflate, br";

const request = function(method, endpoint, authToken, xsrftoken, body, params) {
    let paramString;
    if (params) {
        paramString = Object.keys(params).map(key => key + "=" + params[key]).join("&");
    }

    let url = baseUrl + "/api" + endpoint + (paramString ? "?" + paramString : "");
    let req = [{
        method: method,
        url: url,
        body: body ? JSON.stringify(body) : null,
        params: {
            "cookies": {
                "XSRF-TOKEN": xsrftoken
            },
            "headers": {
                "Host": host,
                "User-Agent": userAgent,
                "Accept": "application/json, text/plain, */*",
                "Accept-Language": acceptLanguage,
                "Accept-Encoding": acceptEncoding,
                "Referer": baseUrl + "/",
                "Authorization": "Bearer " + authToken,
                "X-XSRF-TOKEN": xsrftoken,
                "Content-Type": "application/json",
                "Connection": "keep-alive",
                "TE": "Trailers"
            },
            "tags": {name: url}
        }
    }];

    return http.batch(req);
};

export function login(username, password) {
    let req, res;

    // The user logs in; the authToken gets saved as we need it later
    req = [{
        "method": "post",
        "url": baseUrl + "/api/authenticate",
        "body": "{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"rememberMe\":true}",
        "params": {
            "cookies": {
                "XSRF-TOKEN": defaultXSRFToken
            },
            "headers": {
                "Host": host,
                "User-Agent": userAgent,
                "Accept": "application/json, text/plain, */*",
                "Accept-Language": acceptLanguage,
                "Accept-Encoding": acceptEncoding,
                "Referer": baseUrl + "/",
                "X-XSRF-TOKEN": defaultXSRFToken,
                "Content-Type": "application/json",
                "Connection": "keep-alive",
                "TE": "Trailers"
            },
            "tags": {"name": baseUrl + "/api/authenticate"}
        }
    }];
    res = http.batch(req);
    let authToken = JSON.parse(res[0].body).id_token;
    console.log("GOT authToken " + authToken);

    // The user requests it own information of the account
    req = [{
        "method": "get",
        "url": baseUrl + "/api/account",
        "params": {
            "cookies": {
                "XSRF-TOKEN": defaultXSRFToken
            },
            "headers": {
                "Host": host,
                "User-Agent": userAgent,
                "Accept": "application/json, text/plain, */*",
                "Accept-Language": acceptLanguage,
                "Accept-Encoding": acceptEncoding,
                "Referer": baseUrl + "/",
                "Authorization": "Bearer " + authToken,
                "Connection": "keep-alive",
                "TE": "Trailers"
            },
            "tags": {"name": baseUrl + "/api/account"}
        }
    }];
    res = http.batch(req);
    // A new XSRF Token is needed now, we have to extract it from the cookies
    let xsrftoken = res[0].headers["Set-Cookie"].match('XSRF-TOKEN=(.*); path=\/(; secure)?')[1];

    return {
        get: function(endpoint, params) {
            return request("get", endpoint, authToken, xsrftoken, null, params);
            },
        post: function(endpoint, body, params) {
            return request("post", endpoint, authToken, xsrftoken, body, params);
        },
        put: function(endpoint, body, params) {
            return request("put", endpoint, authToken, xsrftoken, body, params);
        },
        delete: function(endpoint, params) {
            return request("delete", endpoint, authToken, xsrftoken, null, params);
        },
        // Migrated from CodeEditor.js -- Still has to be refactored at some point in time
        simulateSubmissionChanges(exerciseId, participationId, connectionTime) {
            let websocketEndpoint = websocketProtocol + "://" + host + "/websocket/tracker/websocket";
            let websocketUrl = websocketEndpoint + "?access_token=" + authToken;

            let response = ws.connect(websocketUrl, {"tags": {"name": websocketEndpoint}}, function(socket) {
                socket.on('open', function open() {
                    socket.send("CONNECT\nX-XSRF-TOKEN:" + xsrftoken + "\naccept-version:1.1,1.0\nheart-beat:10000,10000\n\n\u0000");
                });

                function getSubscriptionId() {
                    return Math.random().toString(36).replace(/[^a-z]+/g, '').substr(0, 12);
                }

                function submitChange(participationId) {
                    // console.log("Sending changes via WebSocket!");
                    let changeMessage = "SEND\ndestination:/topic/repository/" + participationId + "/files\ncontent-length:73\n\n[{\"fileName\":\"src/de/tum/in/ase/eist/BubbleSort.java\",\"fileContent\":\"a\"}]\u0000";
                    // console.log("Change message is " + changeMessage);
                    socket.send(changeMessage);
                    socket.send("SUBSCRIBE\nid:sub-" + getSubscriptionId() + "\ndestination:/user/topic/repository/" + participationId + "/files\n\n\u0000");
                }

                function subscribe(exerciseId, participationId) {
                    // console.log("Subscribing for changes!");
                    socket.send("SUBSCRIBE\nid:sub-" + getSubscriptionId() + "\ndestination:/topic/participation/" + participationId +"/newResults\n\n\u0000");
                    socket.send("SUBSCRIBE\nid:sub-" + getSubscriptionId() + "\ndestination:/user/topic/exercise/" + exerciseId + "/participation\n\n\u0000");
                }

                socket.setInterval(function timeout() {
                    socket.ping();
                    // console.log("Pinging every 10sec (setInterval test)");
                }, 10000);

                // Send destination and subscription after 1 second
                socket.setTimeout(function() {
                    socket.send("SEND\ndestination:/topic/activity\ncontent-length:20\n\n{\"page\":\"/overview\"}\u0000");
                }, 1 * 1000);

                socket.setTimeout(function() {
                    subscribe(exerciseId, participationId);
                }, 5 * 1000);

                socket.setTimeout(function() {
                    submitChange(participationId);
                }, 10 * 1000);

                socket.setTimeout(function() {
                    // console.log("Committing changes");
                    res = request("post", COMMIT(participationId), authToken, xsrftoken, null, null);
                }, 15 * 1000);

                socket.on('message', function (message) {
                    if (message.startsWith("MESSAGE\ndestination:/topic/participation/" + participationId + "/newResults")) {
                        socket.close();
                        console.log(`RECEIVED new result for test user ` + __VU);
                    }
                });

                socket.setTimeout(function() {
                    socket.close();
                    fail("ERROR: Did not receive result for test user " + __VU);
                }, connectionTime * 1000);
            });
        }
    };
}
