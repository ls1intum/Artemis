(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('participation', {
            parent: 'entity',
            url: '/participation',
            data: {
                authorities: ['ROLE_ADMIN'],
                pageTitle: 'exerciseApplicationApp.participation.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/participation/participations.html',
                    controller: 'ParticipationController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('participation');
                    $translatePartialLoader.addPart('participationState');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('participation-detail', {
            parent: 'entity',
            url: '/participation/{id}',
            data: {
                authorities: ['ROLE_ADMIN'],
                pageTitle: 'exerciseApplicationApp.participation.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/participation/participation-detail.html',
                    controller: 'ParticipationDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('participation');
                    $translatePartialLoader.addPart('participationState');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'Participation', function($stateParams, Participation) {
                    return Participation.get({id : $stateParams.id}).$promise;
                }]
            }
        })
        .state('participation.new', {
            parent: 'participation',
            url: '/new',
            data: {
                authorities: ['ROLE_ADMIN']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/participation/participation-dialog.html',
                    controller: 'ParticipationDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                cloneUrl: null,
                                initializationState: null,
                                initializationDate: null,
                                buildPlanId: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('participation', null, { reload: true });
                }, function() {
                    $state.go('participation');
                });
            }]
        })
        .state('participation.edit', {
            parent: 'participation',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_ADMIN']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/participation/participation-dialog.html',
                    controller: 'ParticipationDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['Participation', function(Participation) {
                            return Participation.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('participation', null, { reload: true });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('participation.delete', {
            parent: 'participation',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_ADMIN']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/participation/participation-delete-dialog.html',
                    controller: 'ParticipationDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['Participation', function(Participation) {
                            return Participation.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('participation', null, { reload: true });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
