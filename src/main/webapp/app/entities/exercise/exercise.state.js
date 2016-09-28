(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('exercise', {
            parent: 'entity',
            url: '/exercise',
            data: {
                authorities: ['ROLE_ADMIN'],
                pageTitle: 'exerciseApplicationApp.exercise.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/exercise/exercises.html',
                    controller: 'ExerciseController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('exercise');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('exercise-detail', {
            parent: 'entity',
            url: '/exercise/{id}',
            data: {
                authorities: ['ROLE_ADMIN'],
                pageTitle: 'exerciseApplicationApp.exercise.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/exercise/exercise-detail.html',
                    controller: 'ExerciseDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('exercise');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'Exercise', function($stateParams, Exercise) {
                    return Exercise.get({id : $stateParams.id}).$promise;
                }]
            }
        })
        .state('exercise.new', {
            parent: 'exercise',
            url: '/new',
            data: {
                authorities: ['ROLE_ADMIN']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/exercise/exercise-dialog.html',
                    controller: 'ExerciseDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                title: null,
                                baseRepositoryUrl: null,
                                baseBuildPlanId: null,
                                publishBuildPlanUrl: null,
                                releaseDate: null,
                                dueDate: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('exercise', null, { reload: true });
                }, function() {
                    $state.go('exercise');
                });
            }]
        })
        .state('exercise.edit', {
            parent: 'exercise',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_ADMIN']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/exercise/exercise-dialog.html',
                    controller: 'ExerciseDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['Exercise', function(Exercise) {
                            return Exercise.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('exercise', null, { reload: true });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('exercise.delete', {
            parent: 'exercise',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_ADMIN']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/exercise/exercise-delete-dialog.html',
                    controller: 'ExerciseDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['Exercise', function(Exercise) {
                            return Exercise.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('exercise', null, { reload: true });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
