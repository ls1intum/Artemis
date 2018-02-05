(function() {
    'use strict';

    angular
        .module('artemisApp')
        .factory('errorHandlerInterceptor', errorHandlerInterceptor);

    errorHandlerInterceptor.$inject = ['$q', '$rootScope'];

    function errorHandlerInterceptor ($q, $rootScope) {
        var service = {
            responseError: responseError
        };

        return service;

        function responseError (response) {
            if (!(response.status === 401 && (response.data === '' || (response.data.path && response.data.path.indexOf('/api/account') === 0 )))) {
                $rootScope.$emit('artemisApp.httpError', response);
            }
            // if (response.status === 403) {
            //     alert("You are not allowed to perform this action.");
            // }
            return $q.reject(response);
        }
    }
})();
