(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('quiz-exercise', {
            parent: 'entity',
            url: '/quiz-exercise',
            data: {
                authorities: ['ROLE_ADMIN', 'ROLE_TA'],
                pageTitle: 'artemisApp.quizExercise.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/quiz-exercise/quiz-exercises.html',
                    controller: 'QuizExerciseController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('quizExercise');
                    $translatePartialLoader.addPart('exercise');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('quiz-exercise-for-course', {
            parent: 'entity',
            url: '/course/{courseid}/quiz-exercise',
            data: {
                authorities: ['ROLE_ADMIN', 'ROLE_TA'],
                pageTitle: 'artemisApp.quizExercise.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/quiz-exercise/quiz-exercises.html',
                    controller: 'QuizExerciseController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('quizExercise');
                    $translatePartialLoader.addPart('exercise');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }],
                courseEntity: ['$stateParams', 'Course', function ($stateParams, Course) {
                    return Course.get({id: $stateParams.courseid}).$promise;
                }]
            }
        })
        .state('quiz-exercise-for-course-detail', {
            parent: 'quiz-exercise',
            url: '/course/{courseid}/quiz-exercise/edit/{id}',
            data: {
                authorities: ['ROLE_ADMIN', 'ROLE_TA'],
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/quiz-exercise/quiz-exercise-detail.html',
                    controller: 'QuizExerciseDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('quizExercise');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'QuizExercise', function($stateParams, QuizExercise) {
                    return QuizExercise.get({id : $stateParams.id}).$promise;
                }],
                courseEntity: ['$stateParams', 'Course', function ($stateParams, Course) {
                    return Course.get({id: $stateParams.courseid}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'quiz-exercise-for-course',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('quiz-exercise-for-course.new', {
            parent: 'quiz-exercise',
            url: '/course/{courseid}/quiz-exercise/new',
            data: {
                authorities: ['ROLE_ADMIN', 'ROLE_TA']
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/quiz-exercise/quiz-exercise-detail.html',
                    controller: 'QuizExerciseDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('quizExercise');
                    return $translate.refresh();
                }],
                entity: function() {
                    return {};
                },
                courseEntity: ['$stateParams', 'Course', function ($stateParams, Course) {
                    console.log($stateParams);
                    return Course.get({id: $stateParams.courseid}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'quiz-exercise-for-course',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('quiz-exercise.edit', {
            parent: 'quiz-exercise',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_ADMIN', 'ROLE_TA']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/quiz-exercise/quiz-exercise-dialog.html',
                    controller: 'QuizExerciseDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['QuizExercise', function(QuizExercise) {
                            return QuizExercise.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('quiz-exercise', null, { reload: 'quiz-exercise' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('quiz-exercise.delete', {
            parent: 'quiz-exercise',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_ADMIN']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/quiz-exercise/quiz-exercise-delete-dialog.html',
                    controller: 'QuizExerciseDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['QuizExercise', function(QuizExercise) {
                            return QuizExercise.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('quiz-exercise', null, { reload: 'quiz-exercise' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
            .state('quiz-exercise-for-course.delete', {
                parent: 'quiz-exercise',
                url: '/{id}/delete',
                data: {
                    authorities: ['ROLE_ADMIN']
                },
                onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                    $uibModal.open({
                        templateUrl: 'app/entities/quiz-exercise/quiz-exercise-delete-dialog.html',
                        controller: 'QuizExerciseDeleteController',
                        controllerAs: 'vm',
                        size: 'md',
                        resolve: {
                            entity: ['QuizExercise', function(QuizExercise) {
                                return QuizExercise.get({id : $stateParams.id}).$promise;
                            }]
                        }
                    }).result.then(function() {
                        $state.go('quiz-exercise-for-course', $state.params, {reload: true});
                    }, function() {
                        $state.go('^');
                    });
                }]
            });
    }

})();
