(function () {
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
                contentContainerClass: 'container-fluid',
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
                    }],
                    exerciseEntity: [function () {
                        return null;
                    }]
                }
            })
            .state('participation-for-exercise', {
                parent: 'entity',
                url: '/exercise/{exerciseId}/participation',
                contentContainerClass: 'container-fluid',
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
                    }],
                    exerciseEntity: ['$stateParams', 'Exercise', function ($stateParams, Exercise) {
                        return Exercise.get({id: $stateParams.exerciseId}).$promise;
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
                    entity: ['$stateParams', 'Participation', function ($stateParams, Participation) {
                        return Participation.get({id: $stateParams.id}).$promise;
                    }]
                }
            })
            .state('participation.new', {
                parent: 'participation',
                url: '/new',
                data: {
                    authorities: ['ROLE_ADMIN']
                },
                onEnter: ['$stateParams', '$state', '$uibModal', function ($stateParams, $state, $uibModal) {
                    $uibModal.open({
                        templateUrl: 'app/entities/participation/participation-dialog.html',
                        controller: 'ParticipationDialogController',
                        controllerAs: 'vm',
                        backdrop: 'static',
                        size: 'lg',
                        resolve: {
                            entity: function () {
                                return {
                                    repositoryUrl: null,
                                    buildPlanId: null,
                                    initializationState: null,
                                    initializationDate: null,
                                    id: null
                                };
                            }
                        }
                    }).result.then(function () {
                        $state.go('participation', null, {reload: true});
                    }, function () {
                        $state.go('participation');
                    });
                }]
            })
            .state('participation-for-exercise.edit', {
                parent: 'participation-for-exercise',
                url: '/{id}/edit',
                data: {
                    authorities: ['ROLE_ADMIN']
                },
                onEnter: ['$stateParams', '$state', '$uibModal', function ($stateParams, $state, $uibModal) {
                    $uibModal.open({
                        templateUrl: 'app/entities/participation/participation-dialog.html',
                        controller: 'ParticipationDialogController',
                        controllerAs: 'vm',
                        backdrop: 'static',
                        size: 'lg',
                        resolve: {
                            entity: ['Participation', function (Participation) {
                                return Participation.get({id: $stateParams.id}).$promise;
                            }]
                        }
                    }).result.then(function () {
                        $state.go('participation-for-exercise',$state.params, {reload: true});
                    }, function () {
                        $state.go('^');
                    });
                }]
            })

            .state('participation.edit', {
                parent: 'participation',
                url: '/{id}/edit',
                data: {
                    authorities: ['ROLE_ADMIN']
                },
                onEnter: ['$stateParams', '$state', '$uibModal', function ($stateParams, $state, $uibModal) {
                    $uibModal.open({
                        templateUrl: 'app/entities/participation/participation-dialog.html',
                        controller: 'ParticipationDialogController',
                        controllerAs: 'vm',
                        backdrop: 'static',
                        size: 'lg',
                        resolve: {
                            entity: ['Participation', function (Participation) {
                                return Participation.get({id: $stateParams.id}).$promise;
                            }]
                        }
                    }).result.then(function () {
                        $state.go('participation', null, {reload: true});
                    }, function () {
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
                onEnter: ['$stateParams', '$state', '$uibModal', function ($stateParams, $state, $uibModal) {
                    $uibModal.open({
                        templateUrl: 'app/entities/participation/participation-delete-dialog.html',
                        controller: 'ParticipationDeleteController',
                        controllerAs: 'vm',
                        size: 'md',
                        resolve: {
                            entity: ['Participation', function (Participation) {
                                return Participation.get({id: $stateParams.id}).$promise;
                            }]
                        }
                    }).result.then(function () {
                        $state.go('participation', null, {reload: true});
                    }, function () {
                        $state.go('^');
                    });
                }]
            })
            .state('participation-for-exercise.delete', {
                parent: 'participation-for-exercise',
                url: '/{id}/delete',
                data: {
                    authorities: ['ROLE_ADMIN']
                },
                onEnter: ['$stateParams', '$state', '$uibModal', function ($stateParams, $state, $uibModal) {
                    $uibModal.open({
                        templateUrl: 'app/entities/participation/participation-delete-dialog.html',
                        controller: 'ParticipationDeleteController',
                        controllerAs: 'vm',
                        size: 'md',
                        resolve: {
                            entity: ['Participation', function (Participation) {
                                return Participation.get({id: $stateParams.id}).$promise;
                            }]
                        }
                    }).result.then(function () {
                        $state.go('participation',$state.params, {reload: true});
                    }, function () {
                        $state.go('^');
                    });
                }]
            });
    }

})();
