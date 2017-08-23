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
                authorities: ['ROLE_USER'],
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
            parent: 'exercise',
            url: '/exercise/{id}',
            data: {
                authorities: ['ROLE_USER'],
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
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'exercise',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('exercise-detail.edit', {
            parent: 'exercise-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
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
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('exercise.new', {
            parent: 'exercise',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
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
                                baseProjectKey: null,
                                baseRepositorySlug: null,
                                baseBuildPlanSlug: null,
                                releaseDate: null,
                                dueDate: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('exercise', null, { reload: 'exercise' });
                }, function() {
                    $state.go('exercise');
                });
            }]
        })
        .state('exercise.edit', {
            parent: 'exercise',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
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
                    $state.go('exercise', null, { reload: 'exercise' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('exercise.delete', {
            parent: 'exercise',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
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
                    $state.go('exercise', null, { reload: 'exercise' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
