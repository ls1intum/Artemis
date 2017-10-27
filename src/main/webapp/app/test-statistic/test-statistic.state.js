(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
            .state('test-statistic', {
            parent: 'app',
            url: '/test-statistic',
            data: {
                authorities: []
            },
            views: {
                'content@': {
                    templateUrl: 'app/test-statistic/test-statistic.html',
                    controller: 'TestStatisticController',
                    controllerAs: 'vm'
                }
            },
        });
    }
})();
