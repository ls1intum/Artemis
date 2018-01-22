(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('statistic', {
            parent: 'entity',
            url: '/statistic',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'artemisApp.statistic.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/statistic/statistics.html',
                    controller: 'StatisticController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('statistic');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('statistic-detail', {
            parent: 'statistic',
            url: '/statistic/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'artemisApp.statistic.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/statistic/statistic-detail.html',
                    controller: 'StatisticDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('statistic');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'Statistic', function($stateParams, Statistic) {
                    return Statistic.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'statistic',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('statistic-detail.edit', {
            parent: 'statistic-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/statistic/statistic-dialog.html',
                    controller: 'StatisticDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['Statistic', function(Statistic) {
                            return Statistic.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('statistic.new', {
            parent: 'statistic',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/statistic/statistic-dialog.html',
                    controller: 'StatisticDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                released: null,
                                participantsRated: null,
                                participantsUnrated: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('statistic', null, { reload: 'statistic' });
                }, function() {
                    $state.go('statistic');
                });
            }]
        })
        .state('statistic.edit', {
            parent: 'statistic',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/statistic/statistic-dialog.html',
                    controller: 'StatisticDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['Statistic', function(Statistic) {
                            return Statistic.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('statistic', null, { reload: 'statistic' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('statistic.delete', {
            parent: 'statistic',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/statistic/statistic-delete-dialog.html',
                    controller: 'StatisticDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['Statistic', function(Statistic) {
                            return Statistic.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('statistic', null, { reload: 'statistic' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
