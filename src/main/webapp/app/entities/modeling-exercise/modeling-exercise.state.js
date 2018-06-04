(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('modeling-exercise', {
            parent: 'entity',
            url: '/modeling-exercise',
            data: {
                authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
                pageTitle: 'artemisApp.modelingExercise.home.title'
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
                    $translatePartialLoader.addPart('exercise');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }],
                courseEntity: [function () {
                    return null;
                }]
            }
        })
        .state('modeling-exercise-for-course', {
            parent: 'entity',
            url: '/course/{courseid}/modeling-exercise',
            data: {
                authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
                pageTitle: 'artemisApp.modelingExercise.home.title'
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
                    $translatePartialLoader.addPart('exercise');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }],
                courseEntity: ['$stateParams', 'Course', function ($stateParams, Course) {
                    return Course.get({id: $stateParams.courseid}).$promise;
                }]
            }
        })
        .state('modeling-exercise-for-course-detail', {
            parent: 'modeling-exercise',
            url: '/course/{courseid}/modeling-exercise/edit/{id}',
            data: {
                authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
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
                courseEntity: ['$stateParams', 'Course', function ($stateParams, Course) {
                    return Course.get({id: $stateParams.courseid}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'modeling-exercise-for-course',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('modeling-exercise.new', {
            parent: 'modeling-exercise',
            url: '/new',
            data: {
                authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN']
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
        .state('modeling-exercise-for-course.new', {
            parent: 'modeling-exercise-for-course',
            url: '/new',
            data: {
                authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/modeling-exercise/modeling-exercise-dialog.html',
                    controller: 'ModelingExerciseDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['Course', function (Course) {
                            return {
                                title: null,
                                baseFilePath: null,
                                releaseDate: null,
                                dueDate: null,
                                maxScore: null,
                                id: null,
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
        .state('modeling-exercise.edit', {
            parent: 'modeling-exercise',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN']
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
                authorities: ['ROLE_ADMIN']
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
        })
        .state('modeling-exercise-for-course.delete', {
            parent: 'modeling-exercise-for-course',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_ADMIN']
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
                    $state.go('modeling-exercise-for-course', $state.params, {reload: true});
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
