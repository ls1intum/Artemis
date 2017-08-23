(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider.state('jhi-tracker', {
            parent: 'admin',
            url: '/tracker',
            data: {
                authorities: ['ROLE_ADMIN'],
                pageTitle: 'tracker.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/admin/tracker/tracker.html',
                    controller: 'JhiTrackerController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('tracker');
                    return $translate.refresh();
                }]
            },
            onEnter: ['JhiTrackerService', function(JhiTrackerService) {
                JhiTrackerService.subscribe();
            }],
            onExit: ['JhiTrackerService', function(JhiTrackerService) {
                JhiTrackerService.unsubscribe();
            }]
        });
    }
})();
