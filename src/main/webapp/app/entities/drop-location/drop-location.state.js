(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('drop-location', {
            parent: 'entity',
            url: '/drop-location',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'artemisApp.dropLocation.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/drop-location/drop-locations.html',
                    controller: 'DropLocationController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('dropLocation');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('drop-location-detail', {
            parent: 'drop-location',
            url: '/drop-location/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'artemisApp.dropLocation.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/drop-location/drop-location-detail.html',
                    controller: 'DropLocationDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('dropLocation');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'DropLocation', function($stateParams, DropLocation) {
                    return DropLocation.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'drop-location',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('drop-location-detail.edit', {
            parent: 'drop-location-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drop-location/drop-location-dialog.html',
                    controller: 'DropLocationDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['DropLocation', function(DropLocation) {
                            return DropLocation.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('drop-location.new', {
            parent: 'drop-location',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drop-location/drop-location-dialog.html',
                    controller: 'DropLocationDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                posX: null,
                                posY: null,
                                width: null,
                                height: null,
                                invalid: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('drop-location', null, { reload: 'drop-location' });
                }, function() {
                    $state.go('drop-location');
                });
            }]
        })
        .state('drop-location.edit', {
            parent: 'drop-location',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drop-location/drop-location-dialog.html',
                    controller: 'DropLocationDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['DropLocation', function(DropLocation) {
                            return DropLocation.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('drop-location', null, { reload: 'drop-location' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('drop-location.delete', {
            parent: 'drop-location',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drop-location/drop-location-delete-dialog.html',
                    controller: 'DropLocationDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['DropLocation', function(DropLocation) {
                            return DropLocation.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('drop-location', null, { reload: 'drop-location' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
