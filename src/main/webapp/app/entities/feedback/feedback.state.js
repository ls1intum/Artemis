(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('feedback', {
            parent: 'entity',
            url: '/feedback',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'artemisApp.feedback.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/feedback/feedbacks.html',
                    controller: 'FeedbackController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('feedback');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('feedback-detail', {
            parent: 'feedback',
            url: '/feedback/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'artemisApp.feedback.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/feedback/feedback-detail.html',
                    controller: 'FeedbackDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('feedback');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'Feedback', function($stateParams, Feedback) {
                    return Feedback.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'feedback',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('feedback-detail.edit', {
            parent: 'feedback-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/feedback/feedback-dialog.html',
                    controller: 'FeedbackDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['Feedback', function(Feedback) {
                            return Feedback.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('feedback.new', {
            parent: 'feedback',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/feedback/feedback-dialog.html',
                    controller: 'FeedbackDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                text: null,
                                detailText: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('feedback', null, { reload: 'feedback' });
                }, function() {
                    $state.go('feedback');
                });
            }]
        })
        .state('feedback.edit', {
            parent: 'feedback',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/feedback/feedback-dialog.html',
                    controller: 'FeedbackDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['Feedback', function(Feedback) {
                            return Feedback.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('feedback', null, { reload: 'feedback' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('feedback.delete', {
            parent: 'feedback',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/feedback/feedback-delete-dialog.html',
                    controller: 'FeedbackDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['Feedback', function(Feedback) {
                            return Feedback.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('feedback', null, { reload: 'feedback' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
