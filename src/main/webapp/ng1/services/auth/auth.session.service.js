(function() {
    'use strict';

    angular
        .module('artemisApp')
        .factory('AuthServerProvider', AuthServerProvider);

    AuthServerProvider.$inject = ['$http', '$localStorage', '$sessionStorage', 'JhiWebsocketService'];

    function AuthServerProvider ($http, $localStorage, $sessionStorage, JhiWebsocketService) {
        var service = {
            getToken: getToken,
            hasValidToken: hasValidToken,
            login: login,
            logout: logout
        };

        return service;

        function getToken () {
            var token = $localStorage.ng1authenticationToken || $sessionStorage.ng1authenticationToken;
            return token;
        }

        function hasValidToken () {
            var token = this.getToken();
            return !!token;
        }

        /*
         * jwt is the jwt token given by the ng5 login and authenticate
         * we changed this from the live version because there were problems
         * with setting two storage tokens
         */
        function login (credentials, jwt) {
            if (credentials.rememberMe) {
                $localStorage.ng1authenticationToken = jwt;
            } else {
                $sessionStorage.ng1authenticationToken = jwt;
            }
            return jwt;
        }

        function logout () {
            JhiWebsocketService.disconnect();
            delete $localStorage.ng1authenticationToken;
            delete $sessionStorage.ng1authenticationToken;
            localStorage.removeItem('jhi-ng1authenticationToken');
            sessionStorage.removeItem('jhi-ng1authenticationToken');
            localStorage.clear();
            sessionStorage.clear();
        }
    }
})();
