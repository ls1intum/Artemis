(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider.state('statistic', {
            parent: 'app',
            url: '/test-statistic',
            data: {
                authorities: []
            },
            views: {
                'content@': {
                    templateUrl: 'app/test-statistic/statistic.html',
                    controller: 'StatisticController',
                    controllerAs: 'vm'
                }
            },
        });
    }
})();
