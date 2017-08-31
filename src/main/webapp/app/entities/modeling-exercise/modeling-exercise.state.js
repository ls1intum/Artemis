(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('modeling-exercise', {
            parent: 'entity',
            url: '/modeling-exercise',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'exerciseApplicationApp.modelingExercise.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/modeling-exercise/modeling-exercises.html',
                    controller: 'ModelingExerciseController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('modelingExercise');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('modeling-exercise-detail', {
            parent: 'modeling-exercise',
            url: '/modeling-exercise/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'exerciseApplicationApp.modelingExercise.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/modeling-exercise/modeling-exercise-detail.html',
                    controller: 'ModelingExerciseDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('modelingExercise');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'ModelingExercise', function($stateParams, ModelingExercise) {
                    return ModelingExercise.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'modeling-exercise',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('modeling-exercise-detail.edit', {
            parent: 'modeling-exercise-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/modeling-exercise/modeling-exercise-dialog.html',
                    controller: 'ModelingExerciseDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['ModelingExercise', function(ModelingExercise) {
                            return ModelingExercise.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('modeling-exercise.new', {
            parent: 'modeling-exercise',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/modeling-exercise/modeling-exercise-dialog.html',
                    controller: 'ModelingExerciseDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                baseFilePath: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('modeling-exercise', null, { reload: 'modeling-exercise' });
                }, function() {
                    $state.go('modeling-exercise');
                });
            }]
        })
        .state('modeling-exercise.edit', {
            parent: 'modeling-exercise',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/modeling-exercise/modeling-exercise-dialog.html',
                    controller: 'ModelingExerciseDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['ModelingExercise', function(ModelingExercise) {
                            return ModelingExercise.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('modeling-exercise', null, { reload: 'modeling-exercise' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('modeling-exercise.delete', {
            parent: 'modeling-exercise',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/modeling-exercise/modeling-exercise-delete-dialog.html',
                    controller: 'ModelingExerciseDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['ModelingExercise', function(ModelingExercise) {
                            return ModelingExercise.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('modeling-exercise', null, { reload: 'modeling-exercise' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
