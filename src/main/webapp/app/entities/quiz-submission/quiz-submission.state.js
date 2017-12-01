(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('quiz-submission', {
            parent: 'entity',
            url: '/quiz-submission',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'artemisApp.quizSubmission.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/quiz-submission/quiz-submissions.html',
                    controller: 'QuizSubmissionController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('quizSubmission');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('quiz-submission-detail', {
            parent: 'quiz-submission',
            url: '/quiz-submission/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'artemisApp.quizSubmission.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/quiz-submission/quiz-submission-detail.html',
                    controller: 'QuizSubmissionDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('quizSubmission');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'QuizSubmission', function($stateParams, QuizSubmission) {
                    return QuizSubmission.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'quiz-submission',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('quiz-submission-detail.edit', {
            parent: 'quiz-submission-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/quiz-submission/quiz-submission-dialog.html',
                    controller: 'QuizSubmissionDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['QuizSubmission', function(QuizSubmission) {
                            return QuizSubmission.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('quiz-submission.new', {
            parent: 'quiz-submission',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/quiz-submission/quiz-submission-dialog.html',
                    controller: 'QuizSubmissionDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                scoreInPoints: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('quiz-submission', null, { reload: 'quiz-submission' });
                }, function() {
                    $state.go('quiz-submission');
                });
            }]
        })
        .state('quiz-submission.edit', {
            parent: 'quiz-submission',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/quiz-submission/quiz-submission-dialog.html',
                    controller: 'QuizSubmissionDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['QuizSubmission', function(QuizSubmission) {
                            return QuizSubmission.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('quiz-submission', null, { reload: 'quiz-submission' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('quiz-submission.delete', {
            parent: 'quiz-submission',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/quiz-submission/quiz-submission-delete-dialog.html',
                    controller: 'QuizSubmissionDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['QuizSubmission', function(QuizSubmission) {
                            return QuizSubmission.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('quiz-submission', null, { reload: 'quiz-submission' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
