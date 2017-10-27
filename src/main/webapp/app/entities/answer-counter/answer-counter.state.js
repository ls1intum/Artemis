(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('answer-counter', {
            parent: 'entity',
            url: '/answer-counter',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'arTeMiSApp.answerCounter.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/answer-counter/answer-counters.html',
                    controller: 'AnswerCounterController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('answerCounter');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('answer-counter-detail', {
            parent: 'answer-counter',
            url: '/answer-counter/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'arTeMiSApp.answerCounter.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/answer-counter/answer-counter-detail.html',
                    controller: 'AnswerCounterDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('answerCounter');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'AnswerCounter', function($stateParams, AnswerCounter) {
                    return AnswerCounter.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'answer-counter',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('answer-counter-detail.edit', {
            parent: 'answer-counter-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/answer-counter/answer-counter-dialog.html',
                    controller: 'AnswerCounterDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['AnswerCounter', function(AnswerCounter) {
                            return AnswerCounter.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('answer-counter.new', {
            parent: 'answer-counter',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/answer-counter/answer-counter-dialog.html',
                    controller: 'AnswerCounterDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                counter: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('answer-counter', null, { reload: 'answer-counter' });
                }, function() {
                    $state.go('answer-counter');
                });
            }]
        })
        .state('answer-counter.edit', {
            parent: 'answer-counter',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/answer-counter/answer-counter-dialog.html',
                    controller: 'AnswerCounterDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['AnswerCounter', function(AnswerCounter) {
                            return AnswerCounter.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('answer-counter', null, { reload: 'answer-counter' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('answer-counter.delete', {
            parent: 'answer-counter',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/answer-counter/answer-counter-delete-dialog.html',
                    controller: 'AnswerCounterDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['AnswerCounter', function(AnswerCounter) {
                            return AnswerCounter.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('answer-counter', null, { reload: 'answer-counter' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
