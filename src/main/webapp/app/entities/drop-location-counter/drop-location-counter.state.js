(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('drop-location-counter', {
            parent: 'entity',
            url: '/drop-location-counter',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'artemisApp.dropLocationCounter.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/drop-location-counter/drop-location-counters.html',
                    controller: 'DropLocationCounterController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('dropLocationCounter');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('drop-location-counter-detail', {
            parent: 'drop-location-counter',
            url: '/drop-location-counter/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'artemisApp.dropLocationCounter.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/drop-location-counter/drop-location-counter-detail.html',
                    controller: 'DropLocationCounterDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('dropLocationCounter');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'DropLocationCounter', function($stateParams, DropLocationCounter) {
                    return DropLocationCounter.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'drop-location-counter',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('drop-location-counter-detail.edit', {
            parent: 'drop-location-counter-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drop-location-counter/drop-location-counter-dialog.html',
                    controller: 'DropLocationCounterDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['DropLocationCounter', function(DropLocationCounter) {
                            return DropLocationCounter.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('drop-location-counter.new', {
            parent: 'drop-location-counter',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drop-location-counter/drop-location-counter-dialog.html',
                    controller: 'DropLocationCounterDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('drop-location-counter', null, { reload: 'drop-location-counter' });
                }, function() {
                    $state.go('drop-location-counter');
                });
            }]
        })
        .state('drop-location-counter.edit', {
            parent: 'drop-location-counter',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drop-location-counter/drop-location-counter-dialog.html',
                    controller: 'DropLocationCounterDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['DropLocationCounter', function(DropLocationCounter) {
                            return DropLocationCounter.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('drop-location-counter', null, { reload: 'drop-location-counter' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('drop-location-counter.delete', {
            parent: 'drop-location-counter',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drop-location-counter/drop-location-counter-delete-dialog.html',
                    controller: 'DropLocationCounterDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['DropLocationCounter', function(DropLocationCounter) {
                            return DropLocationCounter.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('drop-location-counter', null, { reload: 'drop-location-counter' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
