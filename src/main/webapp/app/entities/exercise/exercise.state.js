(function () {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
            .state('exercise', {
                parent: 'entity',
                url: '/exercise',
                contentContainerClass: 'container-fluid',
                data: {
                    authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
                    pageTitle: 'artemisApp.exercise.home.title'
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
                    }],
                    courseEntity: [function () {
                       return null;
                    }]
                }
            })
            .state('exercise-for-course', {
                parent: 'entity',
                url: '/course/{courseid}/exercise',
                contentContainerClass: 'container-fluid',
                data: {
                    authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
                    pageTitle: 'artemisApp.exercise.home.title'
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
                    }],
                    courseEntity: ['$stateParams', 'Course', function ($stateParams, Course) {
                        return Course.get({id: $stateParams.courseid}).$promise;
                    }]
                }
            })
            .state('exercise-detail', {
                parent: 'entity',
                url: '/exercise/{id}',
                data: {
                    authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
                    pageTitle: 'artemisApp.exercise.detail.title'
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
                    entity: ['$stateParams', 'Exercise', function ($stateParams, Exercise) {
                        return Exercise.get({id: $stateParams.id}).$promise;
                    }]
                }
            })
            .state('exercise.new', {
                parent: 'exercise',
                url: '/new',
                data: {
                    authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN']
                },
                onEnter: ['$stateParams', '$state', '$uibModal', function ($stateParams, $state, $uibModal) {
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
                                    maxScore: null,
                                    id: null,
                                    allowOnlineEditor: null
                                };
                            }
                        }
                    }).result.then(function () {
                        $state.go('exercise', null, {reload: true});
                    }, function () {
                        $state.go('exercise');
                    });
                }]
            })
            .state('exercise-for-course.new', {
                parent: 'exercise-for-course',
                url: '/new',
                data: {
                    authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN']
                },
                onEnter: ['$stateParams', '$state', '$uibModal', function ($stateParams, $state, $uibModal) {
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
                                    maxScore: null,
                                    id: null,
                                    allowOnlineEditor: null
                                };
                            }
                        }
                    }).result.then(function () {
                        $state.go('exercise-for-course', $state.params, {reload: true});
                    }, function () {
                        $state.go('exercise-for-course', $state.params);
                    });
                }]
            })
            .state('exercise.edit', {
                parent: 'exercise',
                url: '/{id}/edit',
                data: {
                    authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN']
                },
                onEnter: ['$stateParams', '$state', '$uibModal', function ($stateParams, $state, $uibModal) {
                    $uibModal.open({
                        templateUrl: 'app/entities/exercise/exercise-dialog.html',
                        controller: 'ExerciseDialogController',
                        controllerAs: 'vm',
                        backdrop: 'static',
                        size: 'lg',
                        resolve: {
                            entity: ['Exercise', function (Exercise) {
                                return Exercise.get({id: $stateParams.id}).$promise;
                            }]
                        }
                    }).result.then(function () {
                        $state.go('exercise', null, {reload: true});
                    }, function () {
                        $state.go('^');
                    });
                }]
            })
            .state('exercise-for-course.edit', {
                parent: 'exercise-for-course',
                url: '/{id}/edit',
                data: {
                    authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN']
                },
                onEnter: ['$stateParams', '$state', '$uibModal', function ($stateParams, $state, $uibModal) {
                    $uibModal.open({
                        templateUrl: 'app/entities/exercise/exercise-dialog.html',
                        controller: 'ExerciseDialogController',
                        controllerAs: 'vm',
                        backdrop: 'static',
                        size: 'lg',
                        resolve: {
                            entity: ['Exercise', function (Exercise) {
                                return Exercise.get({id: $stateParams.id}).$promise;
                            }]
                        }
                    }).result.then(function () {
                        $state.go('exercise-for-course', $state.params, {reload: true});
                    }, function () {
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
                onEnter: ['$stateParams', '$state', '$uibModal', function ($stateParams, $state, $uibModal) {
                    $uibModal.open({
                        templateUrl: 'app/entities/exercise/exercise-delete-dialog.html',
                        controller: 'ExerciseDeleteController',
                        controllerAs: 'vm',
                        size: 'md',
                        resolve: {
                            entity: ['Exercise', function (Exercise) {
                                return Exercise.get({id: $stateParams.id}).$promise;
                            }]
                        }
                    }).result.then(function () {
                        $state.go('exercise', null, {reload: true});
                    }, function () {
                        $state.go('^');
                    });
                }]
            })
            .state('exercise-for-course.delete', {
                parent: 'exercise-for-course',
                url: '/{id}/delete',
                data: {
                    authorities: ['ROLE_ADMIN']
                },
                onEnter: ['$stateParams', '$state', '$uibModal', function ($stateParams, $state, $uibModal) {
                    $uibModal.open({
                        templateUrl: 'app/entities/exercise/exercise-delete-dialog.html',
                        controller: 'ExerciseDeleteController',
                        controllerAs: 'vm',
                        size: 'md',
                        resolve: {
                            entity: ['Exercise', function (Exercise) {
                                return Exercise.get({id: $stateParams.id}).$promise;
                            }]
                        }
                    }).result.then(function () {
                        $state.go('exercise-for-course', $state.params, {reload: true});
                    }, function () {
                        $state.go('^');
                    });
                }]
            })
            .state('exercise.reset', {
                parent: 'exercise',
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
                        $state.go('exercise', null, {reload: true});
                    }, function () {
                        $state.go('^');
                    });
                }]
            })
            .state('exercise-for-course.reset', {
                parent: 'exercise-for-course',
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
                        $state.go('exercise-for-course', $state.params, {reload: true});
                    }, function () {
                        $state.go('^');
                    });
                }]
            })
            .state('exercise-detail.ltiConfiguration', {
                parent: 'exercise-detail',
                url: '/{id}/lticonfiguration',
                data: {
                    authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN']
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
                        $state.go('exercise-detail', null, {reload: true});
                    }, function () {
                        $state.go('^');
                    });
                }]
            })
    }

})();
