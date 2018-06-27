(function() {
    'use strict';

    angular
        .module('artemisApp', [
            'ngStorage',
            'ngRoute',
            'tmh.dynamicLocale',
            'pascalprecht.translate',
            'ngResource',
            'ngCookies',
            'ngAria',
            'ngCacheBuster',
            'ngFileUpload',
            'ngFileSaver',
            'ui.bootstrap',
            'ui.bootstrap.datetimepicker',
            'ui.sortable',
            'ui.router',
            'infinite-scroll',
            // jhipster-needle-angularjs-add-module JHipster will add new module here
            'angular-loading-bar',
            'angularMoment',
            'ui.ace',
            'angularResizable',
            'chart.js',
            'ngSanitize',
            'ang-drag-drop'
        ])
        .constant('CONTACT_EMAIL', "krusche@in.tum.de,seitz@in.tum.de,montag@in.tum.de")
        .run(run);

    run.$inject = ['stateHandler', 'translationHandler','$rootScope'];

    function run(stateHandler, translationHandler,$rootScope) {
        stateHandler.initialize();
        translationHandler.initialize();

        $rootScope.$on('$stateChangeSuccess',function(event, toState, toParams, fromState, fromParams){
            console.log('running stateChangeSuccess!');
            $rootScope.contentContainerClass = toState.contentContainerClass ? toState.contentContainerClass : "container-fluid";
            $rootScope.bodyClass = toState.bodyClass ? toState.bodyClass : "";
        });

    }

    require("./app.config");
    var context = require.context(".", true, /^(?!\.\/bower_components\/).*\.(js|html)$/);
    context.keys().forEach(function (key) {
        context(key);
    });
})();
