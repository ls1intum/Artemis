(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('programming-exercise', {
            parent: 'entity',
            url: '/programming-exercise',
            data: {
                authorities: ['ROLE_ADMIN', 'ROLE_TA'],
                pageTitle: 'artemisApp.programmingExercise.home.title'
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
                    $translatePartialLoader.addPart('exercise');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }],
                courseEntity: [function () {
                    return null;
                }]
            }
        })
        .state('programming-exercise-for-course', {
            parent: 'entity',
            url: '/course/{courseid}/programming-exercise',
            data: {
                authorities: ['ROLE_ADMIN', 'ROLE_TA'],
                pageTitle: 'artemisApp.programmingExercise.home.title'
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
                    $translatePartialLoader.addPart('exercise');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }],
                courseEntity: ['$stateParams', 'Course', function ($stateParams, Course) {
                    return Course.get({id: $stateParams.courseid}).$promise;
                }]
            }
        })
        .state('programming-exercise-detail', {
            parent: 'entity',
            url: '/programming-exercise/{id}',
            data: {
                authorities: ['ROLE_ADMIN', 'ROLE_TA'],
                pageTitle: 'artemisApp.programmingExercise.detail.title'
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
                authorities: ['ROLE_ADMIN', 'ROLE_TA']
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
                authorities: ['ROLE_ADMIN', 'ROLE_TA']
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
        .state('programming-exercise-for-course.new', {
            parent: 'programming-exercise-for-course',
            url: '/new',
            data: {
                authorities: ['ROLE_ADMIN', 'ROLE_TA']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/programming-exercise/programming-exercise-dialog.html',
                    controller: 'ProgrammingExerciseDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['Course', function (Course) {
                            return {
                                title: null,
                                baseRepositoryUrl: null,
                                baseBuildPlanId: null,
                                publishBuildPlanUrl: null,
                                releaseDate: null,
                                dueDate: null,
                                id: null,
                                allowOnlineEditor: null,
                                course: Course.get({id: $stateParams.courseid})
                            };
                        }]
                    }
                }).result.then(function() {
                    $state.go('programming-exercise-for-course', $state.params, {reload: true});
                }, function() {
                    $state.go('programming-exercise-for-course');
                });
            }]
        })
        .state('programming-exercise.edit', {
            parent: 'programming-exercise',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_ADMIN', 'ROLE_TA']
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
        .state('programming-exercise-for-course.edit', {
            parent: 'programming-exercise-for-course',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_ADMIN', 'ROLE_TA']
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
                    $state.go('programming-exercise-for-course', $state.params, {reload: true});
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('programming-exercise.delete', {
            parent: 'programming-exercise',
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
                    $state.go('programming-exercise', null, { reload: 'programming-exercise' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('programming-exercise-for-course.delete', {
            parent: 'programming-exercise-for-course',
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
                    $state.go('programming-exercise-for-course', $state.params, {reload: true});
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('programming-exercise.reset', {
            parent: 'programming-exercise',
            url: '/{id}/reset',
            data: {
                authorities: ['ROLE_ADMIN']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function ($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/exercise/exercise-reset-dialog.html',
                    controller: 'ExerciseResetController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['Exercise', function (Exercise) {
                            return Exercise.get({id: $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function () {
                    $state.go('programming-exercise', null, {reload: true});
                }, function () {
                    $state.go('^');
                });
            }]
        })
        .state('programming-exercise-for-course.reset', {
            parent: 'programming-exercise-for-course',
            url: '/{id}/reset',
            data: {
                authorities: ['ROLE_ADMIN']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function ($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/exercise/exercise-reset-dialog.html',
                    controller: 'ExerciseResetController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['Exercise', function (Exercise) {
                            return Exercise.get({id: $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function () {
                    $state.go('programming-exercise-for-course', $state.params, {reload: true});
                }, function () {
                    $state.go('^');
                });
            }]
        })
        .state('programming-exercise-detail.ltiConfiguration', {
            parent: 'programming-exercise-detail',
            url: '/{id}/lticonfiguration',
            data: {
                authorities: ['ROLE_ADMIN', 'ROLE_TA']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function ($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/exercise/exercise-lti-configuration-dialog.html',
                    controller: 'ExerciseLtiConfigurationDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        exercise: ['Exercise', function (Exercise) {
                            return Exercise.get({id: $stateParams.id}).$promise;
                        }],
                        configuration: ['ExerciseLtiConfiguration', function (ExerciseLtiConfiguration) {
                            return ExerciseLtiConfiguration.get({exerciseId: $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function () {
                    $state.go('programming-exercise-detail', null, {reload: true});
                }, function () {
                    $state.go('^');
                });
            }]
        });
    }

})();
