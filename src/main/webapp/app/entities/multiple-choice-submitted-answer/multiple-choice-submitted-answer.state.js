(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('multiple-choice-submitted-answer', {
            parent: 'entity',
            url: '/multiple-choice-submitted-answer',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'exerciseApplicationApp.multipleChoiceSubmittedAnswer.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answers.html',
                    controller: 'MultipleChoiceSubmittedAnswerController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('multipleChoiceSubmittedAnswer');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('multiple-choice-submitted-answer-detail', {
            parent: 'multiple-choice-submitted-answer',
            url: '/multiple-choice-submitted-answer/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'exerciseApplicationApp.multipleChoiceSubmittedAnswer.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answer-detail.html',
                    controller: 'MultipleChoiceSubmittedAnswerDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('multipleChoiceSubmittedAnswer');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'MultipleChoiceSubmittedAnswer', function($stateParams, MultipleChoiceSubmittedAnswer) {
                    return MultipleChoiceSubmittedAnswer.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'multiple-choice-submitted-answer',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('multiple-choice-submitted-answer-detail.edit', {
            parent: 'multiple-choice-submitted-answer-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answer-dialog.html',
                    controller: 'MultipleChoiceSubmittedAnswerDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['MultipleChoiceSubmittedAnswer', function(MultipleChoiceSubmittedAnswer) {
                            return MultipleChoiceSubmittedAnswer.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('multiple-choice-submitted-answer.new', {
            parent: 'multiple-choice-submitted-answer',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answer-dialog.html',
                    controller: 'MultipleChoiceSubmittedAnswerDialogController',
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
                    $state.go('multiple-choice-submitted-answer', null, { reload: 'multiple-choice-submitted-answer' });
                }, function() {
                    $state.go('multiple-choice-submitted-answer');
                });
            }]
        })
        .state('multiple-choice-submitted-answer.edit', {
            parent: 'multiple-choice-submitted-answer',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answer-dialog.html',
                    controller: 'MultipleChoiceSubmittedAnswerDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['MultipleChoiceSubmittedAnswer', function(MultipleChoiceSubmittedAnswer) {
                            return MultipleChoiceSubmittedAnswer.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('multiple-choice-submitted-answer', null, { reload: 'multiple-choice-submitted-answer' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('multiple-choice-submitted-answer.delete', {
            parent: 'multiple-choice-submitted-answer',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answer-delete-dialog.html',
                    controller: 'MultipleChoiceSubmittedAnswerDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['MultipleChoiceSubmittedAnswer', function(MultipleChoiceSubmittedAnswer) {
                            return MultipleChoiceSubmittedAnswer.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('multiple-choice-submitted-answer', null, { reload: 'multiple-choice-submitted-answer' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
