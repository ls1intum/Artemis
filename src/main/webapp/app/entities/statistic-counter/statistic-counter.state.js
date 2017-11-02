(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('statistic-counter', {
            parent: 'entity',
            url: '/statistic-counter',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'arTeMiSApp.statisticCounter.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/statistic-counter/statistic-counters.html',
                    controller: 'StatisticCounterController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('statisticCounter');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('statistic-counter-detail', {
            parent: 'statistic-counter',
            url: '/statistic-counter/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'arTeMiSApp.statisticCounter.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/statistic-counter/statistic-counter-detail.html',
                    controller: 'StatisticCounterDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('statisticCounter');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'StatisticCounter', function($stateParams, StatisticCounter) {
                    return StatisticCounter.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'statistic-counter',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('statistic-counter-detail.edit', {
            parent: 'statistic-counter-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/statistic-counter/statistic-counter-dialog.html',
                    controller: 'StatisticCounterDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['StatisticCounter', function(StatisticCounter) {
                            return StatisticCounter.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('statistic-counter.new', {
            parent: 'statistic-counter',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/statistic-counter/statistic-counter-dialog.html',
                    controller: 'StatisticCounterDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                counter: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('statistic-counter', null, { reload: 'statistic-counter' });
                }, function() {
                    $state.go('statistic-counter');
                });
            }]
        })
        .state('statistic-counter.edit', {
            parent: 'statistic-counter',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/statistic-counter/statistic-counter-dialog.html',
                    controller: 'StatisticCounterDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['StatisticCounter', function(StatisticCounter) {
                            return StatisticCounter.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('statistic-counter', null, { reload: 'statistic-counter' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('statistic-counter.delete', {
            parent: 'statistic-counter',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/statistic-counter/statistic-counter-delete-dialog.html',
                    controller: 'StatisticCounterDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['StatisticCounter', function(StatisticCounter) {
                            return StatisticCounter.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('statistic-counter', null, { reload: 'statistic-counter' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
