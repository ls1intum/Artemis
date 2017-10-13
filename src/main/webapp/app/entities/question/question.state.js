(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('question', {
            parent: 'entity',
            url: '/question',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'artemisApp.question.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/question/questions.html',
                    controller: 'QuestionController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('question');
                    $translatePartialLoader.addPart('scoringType');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('question-detail', {
            parent: 'question',
            url: '/question/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'artemisApp.question.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/question/question-detail.html',
                    controller: 'QuestionDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('question');
                    $translatePartialLoader.addPart('scoringType');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'Question', function($stateParams, Question) {
                    return Question.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'question',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('question-detail.edit', {
            parent: 'question-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/question/question-dialog.html',
                    controller: 'QuestionDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['Question', function(Question) {
                            return Question.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('question.new', {
            parent: 'question',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/question/question-dialog.html',
                    controller: 'QuestionDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                title: null,
                                text: null,
                                hint: null,
                                explanation: null,
                                score: null,
                                scoringType: null,
                                randomizeOrder: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('question', null, { reload: 'question' });
                }, function() {
                    $state.go('question');
                });
            }]
        })
        .state('question.edit', {
            parent: 'question',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/question/question-dialog.html',
                    controller: 'QuestionDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['Question', function(Question) {
                            return Question.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('question', null, { reload: 'question' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('question.delete', {
            parent: 'question',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/question/question-delete-dialog.html',
                    controller: 'QuestionDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['Question', function(Question) {
                            return Question.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('question', null, { reload: 'question' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
