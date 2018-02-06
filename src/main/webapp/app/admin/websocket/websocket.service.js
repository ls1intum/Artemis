(function() {
    'use strict';
    /* globals SockJS, Stomp */

    angular
        .module('artemisApp')
        .factory('JhiWebsocketService', JhiWebsocketService);

    JhiWebsocketService.$inject = ['$rootScope', '$window', '$cookies', '$http', '$q'];

    function JhiWebsocketService ($rootScope, $window, $cookies, $http, $q) {
        var stompClient = null;
        var subscriber = {};
        var listener = {};
        var connectListener = [];
        var disconnectListener = [];
        var connected = $q.defer();
        var alreadyConnectedOnce = false;
        var isConnected = false;
        var consecutiveFailedAttempts = 0;
        var shouldReconnect = false;

        var service = {
            connect: connect,
            disconnect: disconnect,
            receive: receive,
            sendActivity: sendActivity,
            send: send,
            subscribe: subscribe,
            unsubscribe: unsubscribe,
            bind: bind,
            unbind: unbind,
            enableReconnect: enableReconnect,
            disableReconnect: disableReconnect
        };

        return service;

        //adapted from https://stackoverflow.com/questions/22361917/automatic-reconnect-with-stomp-js-in-node-js-application
        function stompFailureCallback(error) {
            // console.error("Websocket disconnect due to: " + error); // this console.error is not needed, because error is already logged by stomp-websocket
            isConnected = false;
            consecutiveFailedAttempts++;
            disconnectListener.forEach(function (listener) {
                listener();
            });
            if (shouldReconnect) {
                // NOTE: after 5 failed attempts in row, increase the timeout to 5 seconds,
                // after 10 failed attempts in row, increase the timeout to 10 seconds
                // after 20 failed attempts in row, increase the timeout to 20 seconds
                // after 30 failed attempts in row, increase the timeout to 60 seconds
                var waitUntilReconnectAttempt;
                if (consecutiveFailedAttempts > 30) {
                    waitUntilReconnectAttempt = 60
                } else if (consecutiveFailedAttempts > 20) {
                    waitUntilReconnectAttempt = 20
                } else if (consecutiveFailedAttempts > 10) {
                    waitUntilReconnectAttempt = 10;
                } else if (consecutiveFailedAttempts > 5) {
                    waitUntilReconnectAttempt = 5;
                } else {
                    waitUntilReconnectAttempt = 1;
                }
                setTimeout(connect, waitUntilReconnectAttempt * 1000);
                console.log("Websocket: Try to reconnect in " + waitUntilReconnectAttempt + " seconds...");
            }
        }

        function connect () {
            if (isConnected) {
                return; // don't connect, if already connected
            }
            //building absolute path so that websocket doesn't fail when deploying with a context path
            var loc = $window.location;
            var url = '//' + loc.host + loc.pathname + 'websocket/tracker';
            var socket = new SockJS(url);
            stompClient = Stomp.over(socket);
            var stateChangeStart;
            var headers = {};
            headers[$http.defaults.xsrfHeaderName] = $cookies.get($http.defaults.xsrfCookieName);
            stompClient.connect(headers, function() {
                connected.resolve('success');
                connectListener.forEach(function (listener) {
                    listener();
                });
                isConnected = true;
                consecutiveFailedAttempts = 0;
                // (re)connect to all existing channels
                // Note: use function instead of for-loop to prevent
                // variable "channel" from being mutated
                Object.keys(listener).forEach(function (channel) {
                    subscriber[channel] = stompClient.subscribe(channel, function(data) {
                        listener[channel].notify(angular.fromJson(data.body));
                    });
                });
                sendActivity();
                if (!alreadyConnectedOnce) {
                    stateChangeStart = $rootScope.$on('$stateChangeStart', function () {
                        sendActivity();
                    });
                    alreadyConnectedOnce = true;
                }
            }, stompFailureCallback);
            $rootScope.$on('$destroy', function () {
                if(angular.isDefined(stateChangeStart) && stateChangeStart !== null){
                    stateChangeStart();
                }
            });
        }

        function disconnect () {
            console.log(listener);
            Object.keys(listener).forEach(unsubscribe);
            console.log(listener);
            if (stompClient !== null) {
                stompClient.disconnect();
                stompClient = null;
            }
        }

        function receive (channel) {
            if(!listener[channel]) {
                listener[channel] = $q.defer();
            }
            return listener[channel].promise;
        }

        function sendActivity() {
            if (stompClient !== null && stompClient.connected) {
                stompClient
                    .send('/topic/activity',
                        {},
                        angular.toJson({'page': $rootScope.toState.name}));
            }
        }

        /**
         * Send data through the websocket connection
         * @param path {string} the path for the websocket connection
         * @param data {object} the date to send through the websocket connection
         */
        function send(path, data) {
            if (stompClient !== null && stompClient.connected) {
                stompClient
                    .send(path,
                        {},
                        angular.toJson(data));
            }
        }

        function subscribe (channel) {
            connected.promise.then(function() {
                if(!listener[channel]) {
                    listener[channel] = $q.defer();
                }
                subscriber[channel] = stompClient.subscribe(channel, function(data) {
                    listener[channel].notify(angular.fromJson(data.body));
                });
            }, null, null);
        }

        function unsubscribe (channel) {
            if (subscriber[channel] != null) {  // '!=' is necessary to also test for undefined
                subscriber[channel].unsubscribe();
            }
            delete listener[channel];
            delete subscriber[channel];
        }

        /**
         * bind the given callback function to the given event
         *
         * @param event {string} the event to be notified of
         * @param callback {function} the function to call when the event is triggered
         */
        function bind(event, callback) {
            switch (event) {
                case "connect":
                    connectListener.push(callback);
                    if (isConnected) {
                        callback();
                    }
                    break;
                case "disconnect":
                    disconnectListener.push(callback);
                    if (!isConnected) {
                        callback();
                    }
                    break;
            }
        }

        /**
         * unbind the given callback function from the given event
         *
         * @param event {string} the event to no longer be notified of
         * @param callback {function} the function to no longer call when the event is triggered
         */
        function unbind(event, callback) {
            switch (event) {
                case "connect":
                    connectListener = connectListener.filter(function(listener) {
                        return listener !== callback;
                    });
                    break;
                case "disconnect":
                    disconnectListener = disconnectListener.filter(function(listener) {
                        return listener !== callback;
                    });
                    break;
            }
        }

        /**
         * enable automatic reconnect
         */
        function enableReconnect() {
            shouldReconnect = true;
            if(!isConnected) {
                connect();
            }
        }

        /**
         * disable automatic reconnect
         */
        function disableReconnect() {
            shouldReconnect = false;
        }
    }
})();
