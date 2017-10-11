(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('answer-option', {
            parent: 'entity',
            url: '/answer-option',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'artemisApp.answerOption.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/answer-option/answer-options.html',
                    controller: 'AnswerOptionController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('answerOption');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('answer-option-detail', {
            parent: 'answer-option',
            url: '/answer-option/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'artemisApp.answerOption.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/answer-option/answer-option-detail.html',
                    controller: 'AnswerOptionDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('answerOption');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'AnswerOption', function($stateParams, AnswerOption) {
                    return AnswerOption.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'answer-option',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('answer-option-detail.edit', {
            parent: 'answer-option-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/answer-option/answer-option-dialog.html',
                    controller: 'AnswerOptionDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['AnswerOption', function(AnswerOption) {
                            return AnswerOption.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('answer-option.new', {
            parent: 'answer-option',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/answer-option/answer-option-dialog.html',
                    controller: 'AnswerOptionDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                text: null,
                                hint: null,
                                explanation: null,
                                isCorrect: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('answer-option', null, { reload: 'answer-option' });
                }, function() {
                    $state.go('answer-option');
                });
            }]
        })
        .state('answer-option.edit', {
            parent: 'answer-option',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/answer-option/answer-option-dialog.html',
                    controller: 'AnswerOptionDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['AnswerOption', function(AnswerOption) {
                            return AnswerOption.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('answer-option', null, { reload: 'answer-option' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('answer-option.delete', {
            parent: 'answer-option',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/answer-option/answer-option-delete-dialog.html',
                    controller: 'AnswerOptionDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['AnswerOption', function(AnswerOption) {
                            return AnswerOption.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('answer-option', null, { reload: 'answer-option' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
