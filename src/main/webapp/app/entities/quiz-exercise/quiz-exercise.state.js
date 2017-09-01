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
                authorities: ['ROLE_USER'],
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
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('quiz-exercise-detail', {
            parent: 'quiz-exercise',
            url: '/quiz-exercise/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'artemisApp.quizExercise.detail.title'
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
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'quiz-exercise',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('quiz-exercise-detail.edit', {
            parent: 'quiz-exercise-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
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
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('quiz-exercise.new', {
            parent: 'quiz-exercise',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/quiz-exercise/quiz-exercise-dialog.html',
                    controller: 'QuizExerciseDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('quiz-exercise', null, { reload: 'quiz-exercise' });
                }, function() {
                    $state.go('quiz-exercise');
                });
            }]
        })
        .state('quiz-exercise.edit', {
            parent: 'quiz-exercise',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
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
                authorities: ['ROLE_USER']
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
        });
    }

})();
