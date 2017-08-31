(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('programming-exercise', {
            parent: 'entity',
            url: '/programming-exercise',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'exerciseApplicationApp.programmingExercise.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/programming-exercise/programming-exercises.html',
                    controller: 'ProgrammingExerciseController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('programmingExercise');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('programming-exercise-detail', {
            parent: 'programming-exercise',
            url: '/programming-exercise/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'exerciseApplicationApp.programmingExercise.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/programming-exercise/programming-exercise-detail.html',
                    controller: 'ProgrammingExerciseDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('programmingExercise');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'ProgrammingExercise', function($stateParams, ProgrammingExercise) {
                    return ProgrammingExercise.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'programming-exercise',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('programming-exercise-detail.edit', {
            parent: 'programming-exercise-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/programming-exercise/programming-exercise-dialog.html',
                    controller: 'ProgrammingExerciseDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['ProgrammingExercise', function(ProgrammingExercise) {
                            return ProgrammingExercise.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('programming-exercise.new', {
            parent: 'programming-exercise',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/programming-exercise/programming-exercise-dialog.html',
                    controller: 'ProgrammingExerciseDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                baseRepositoryUrl: null,
                                baseBuildPlanId: null,
                                publishBuildPlanUrl: null,
                                allowOnlineEditor: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('programming-exercise', null, { reload: 'programming-exercise' });
                }, function() {
                    $state.go('programming-exercise');
                });
            }]
        })
        .state('programming-exercise.edit', {
            parent: 'programming-exercise',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/programming-exercise/programming-exercise-dialog.html',
                    controller: 'ProgrammingExerciseDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['ProgrammingExercise', function(ProgrammingExercise) {
                            return ProgrammingExercise.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('programming-exercise', null, { reload: 'programming-exercise' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('programming-exercise.delete', {
            parent: 'programming-exercise',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/programming-exercise/programming-exercise-delete-dialog.html',
                    controller: 'ProgrammingExerciseDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['ProgrammingExercise', function(ProgrammingExercise) {
                            return ProgrammingExercise.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('programming-exercise', null, { reload: 'programming-exercise' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
