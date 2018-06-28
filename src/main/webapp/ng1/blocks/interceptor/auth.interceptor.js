(function() {
    'use strict';

    angular
        .module('artemisApp')
        .factory('authInterceptor', authInterceptor);

    authInterceptor.$inject = ['$q', '$localStorage', '$sessionStorage'];

    /**
     * @function authInterceptor
     * @param $q
     * @param $localStorage
     * @param $sessionStorage
     * @desc This interceptor implementation was adapted from how the Angular5 app integrates the bearer token
     * for authentication purposes on API calls in the header.
     * It checks the local storage and the session for the token and, if available, adds it to the request's header.
     */
    function authInterceptor($q, $localStorage, $sessionStorage) {
        var service = {
            request: request
        };

        return service;

        function request(config) {
            var token = $localStorage.ng1authenticationToken || $sessionStorage.ng1authenticationToken;
            if (!!token) {
                config.headers.Authorization = 'Bearer ' + token;
            }
            return config;
        }
    }
})();
