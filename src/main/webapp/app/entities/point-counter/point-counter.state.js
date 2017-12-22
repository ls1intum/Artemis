(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('point-counter', {
            parent: 'entity',
            url: '/point-counter',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'arTeMiSApp.pointCounter.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/point-counter/point-counters.html',
                    controller: 'PointCounterController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('pointCounter');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('point-counter-detail', {
            parent: 'point-counter',
            url: '/point-counter/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'arTeMiSApp.pointCounter.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/point-counter/point-counter-detail.html',
                    controller: 'PointCounterDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('pointCounter');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'PointCounter', function($stateParams, PointCounter) {
                    return PointCounter.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'point-counter',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('point-counter-detail.edit', {
            parent: 'point-counter-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/point-counter/point-counter-dialog.html',
                    controller: 'PointCounterDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['PointCounter', function(PointCounter) {
                            return PointCounter.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('point-counter.new', {
            parent: 'point-counter',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/point-counter/point-counter-dialog.html',
                    controller: 'PointCounterDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                points: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('point-counter', null, { reload: 'point-counter' });
                }, function() {
                    $state.go('point-counter');
                });
            }]
        })
        .state('point-counter.edit', {
            parent: 'point-counter',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/point-counter/point-counter-dialog.html',
                    controller: 'PointCounterDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['PointCounter', function(PointCounter) {
                            return PointCounter.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('point-counter', null, { reload: 'point-counter' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('point-counter.delete', {
            parent: 'point-counter',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/point-counter/point-counter-delete-dialog.html',
                    controller: 'PointCounterDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['PointCounter', function(PointCounter) {
                            return PointCounter.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('point-counter', null, { reload: 'point-counter' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
