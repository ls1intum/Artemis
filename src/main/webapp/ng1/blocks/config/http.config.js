(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(httpConfig);

    httpConfig.$inject = ['$urlRouterProvider', '$httpProvider', 'httpRequestInterceptorCacheBusterProvider', '$urlMatcherFactoryProvider'];

    function httpConfig($urlRouterProvider, $httpProvider, httpRequestInterceptorCacheBusterProvider, $urlMatcherFactoryProvider) {
        //Cache everything except rest api requests
        httpRequestInterceptorCacheBusterProvider.setMatchlist([/.*api.*/, /.*protected.*/], true);

        /**
         * This breaks the routing of the Angular application, that's why we need to disable it
         * It would redirect to '/' whenever the route can't be matched, which can be the case for upgraded components
          */
        //$urlRouterProvider.otherwise('/');

        $httpProvider.defaults.cache = false;

        /**
         * Register the defined interceptors
         */
        $httpProvider.interceptors.push('authInterceptor');
        $httpProvider.interceptors.push('errorHandlerInterceptor');
        $httpProvider.interceptors.push('authExpiredInterceptor');
        $httpProvider.interceptors.push('notificationInterceptor');
        // jhipster-needle-angularjs-add-interceptor JHipster will add new application http interceptor here

        $urlMatcherFactoryProvider.type('boolean', {
            name : 'boolean',
            decode: function(val) { return val === true || val === 'true'; },
            encode: function(val) { return val ? 1 : 0; },
            equals: function(a, b) { return this.is(a) && a === b; },
            is: function(val) { return [true,false,0,1].indexOf(val) >= 0; },
            pattern: /bool|true|0|1/
        });
    }
})();
