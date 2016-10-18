(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp', [
            'ngStorage',
            'tmh.dynamicLocale',
            'pascalprecht.translate',
            'ngResource',
            'ngCookies',
            'ngAria',
            'ngCacheBuster',
            'ngFileUpload',
            'ui.bootstrap',
            'ui.bootstrap.datetimepicker',
            'ui.router',
            'infinite-scroll',
            // jhipster-needle-angularjs-add-module JHipster will add new module here
            'angular-loading-bar',
            'angularMoment'
        ])
        .run(run);

    run.$inject = ['stateHandler', 'translationHandler','$rootScope'];

    function run(stateHandler, translationHandler,$rootScope) {
        stateHandler.initialize();
        translationHandler.initialize();

        $rootScope.$on('$stateChangeSuccess',function(event, toState, toParams, fromState, fromParams){
            $rootScope.contentContainerClass = toState.contentContainerClass ? toState.contentContainerClass : "container";
            $rootScope.bodyClass = toState.bodyClass ? toState.bodyClass : "";
        });


    }
})();
