(function() {
    'use strict';
    /* globals SockJS, Stomp */

    angular
        .module('artemisApp')
        .factory('JhiWebsocketService', JhiWebsocketService);

    JhiWebsocketService.$inject = ['$rootScope', '$window', '$cookies', '$http', '$q'];

    function JhiWebsocketService ($rootScope, $window, $cookies, $http, $q) {
        var stompClient = null;
        var subscriber = null;
        var listener = {};
        var connected = $q.defer();
        var alreadyConnectedOnce = false;

        var service = {
            connect: connect,
            disconnect: disconnect,
            receive: receive,
            sendActivity: sendActivity,
            subscribe: subscribe,
            unsubscribe: unsubscribe
        };

        return service;

        function connect () {
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
                sendActivity();
                if (!alreadyConnectedOnce) {
                    stateChangeStart = $rootScope.$on('$stateChangeStart', function () {
                        sendActivity();
                    });
                    alreadyConnectedOnce = true;
                }
            });
            $rootScope.$on('$destroy', function () {
                if(angular.isDefined(stateChangeStart) && stateChangeStart !== null){
                    stateChangeStart();
                }
            });
        }

        function disconnect () {
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

        function subscribe (channel) {
            connected.promise.then(function() {
                if(!listener[channel]) {
                    listener[channel] = $q.defer();
                }
                subscriber = stompClient.subscribe(channel, function(data) {
                    listener[channel].notify(angular.fromJson(data.body));
                });
            }, null, null);
        }

        function unsubscribe (channel) {
            if (subscriber !== null) {
                subscriber.unsubscribe();
            }
            listener[channel] = $q.defer();
        }
    }
})();
