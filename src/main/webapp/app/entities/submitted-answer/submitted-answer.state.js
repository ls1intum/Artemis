(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('submitted-answer', {
            parent: 'entity',
            url: '/submitted-answer',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'artemisApp.submittedAnswer.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/submitted-answer/submitted-answers.html',
                    controller: 'SubmittedAnswerController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('submittedAnswer');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('submitted-answer-detail', {
            parent: 'submitted-answer',
            url: '/submitted-answer/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'artemisApp.submittedAnswer.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/submitted-answer/submitted-answer-detail.html',
                    controller: 'SubmittedAnswerDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('submittedAnswer');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'SubmittedAnswer', function($stateParams, SubmittedAnswer) {
                    return SubmittedAnswer.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'submitted-answer',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('submitted-answer-detail.edit', {
            parent: 'submitted-answer-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/submitted-answer/submitted-answer-dialog.html',
                    controller: 'SubmittedAnswerDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['SubmittedAnswer', function(SubmittedAnswer) {
                            return SubmittedAnswer.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('submitted-answer.new', {
            parent: 'submitted-answer',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/submitted-answer/submitted-answer-dialog.html',
                    controller: 'SubmittedAnswerDialogController',
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
                    $state.go('submitted-answer', null, { reload: 'submitted-answer' });
                }, function() {
                    $state.go('submitted-answer');
                });
            }]
        })
        .state('submitted-answer.edit', {
            parent: 'submitted-answer',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/submitted-answer/submitted-answer-dialog.html',
                    controller: 'SubmittedAnswerDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['SubmittedAnswer', function(SubmittedAnswer) {
                            return SubmittedAnswer.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('submitted-answer', null, { reload: 'submitted-answer' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('submitted-answer.delete', {
            parent: 'submitted-answer',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/submitted-answer/submitted-answer-delete-dialog.html',
                    controller: 'SubmittedAnswerDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['SubmittedAnswer', function(SubmittedAnswer) {
                            return SubmittedAnswer.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('submitted-answer', null, { reload: 'submitted-answer' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
