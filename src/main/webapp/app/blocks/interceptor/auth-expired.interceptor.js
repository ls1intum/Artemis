(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .factory('authExpiredInterceptor', authExpiredInterceptor);


    authExpiredInterceptor.$inject = ['$rootScope', '$q', '$injector', '$document', '$location'];

    function authExpiredInterceptor($rootScope, $q, $injector, $document, $location) {
        var service = {
            responseError: responseError
        };

        return service;

        function responseError(response) {
            // If we have an unauthorized request we redirect to the login page
            // Don't do this check on the account API to avoid infinite loop
            if (response.status === 401 && angular.isDefined(response.data.path) && response.data.path.indexOf('/api/account') === -1) {
                var Auth = $injector.get('Auth');
                var to = $rootScope.toState;
                var params = $rootScope.toStateParams;
                if(!$location.search().login) {
                    Auth.logout();
                }
                if (to.name !== 'accessdenied') {
                    Auth.storePreviousState(to.name, params);
                }
                var LoginService = $injector.get('LoginService');
                LoginService.open();
            }
            return $q.reject(response);
        }
    }
})();
