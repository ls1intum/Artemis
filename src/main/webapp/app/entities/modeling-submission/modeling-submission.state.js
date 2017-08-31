(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('modeling-submission', {
            parent: 'entity',
            url: '/modeling-submission',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'exerciseApplicationApp.modelingSubmission.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/modeling-submission/modeling-submissions.html',
                    controller: 'ModelingSubmissionController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('modelingSubmission');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('modeling-submission-detail', {
            parent: 'modeling-submission',
            url: '/modeling-submission/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'exerciseApplicationApp.modelingSubmission.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/modeling-submission/modeling-submission-detail.html',
                    controller: 'ModelingSubmissionDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('modelingSubmission');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'ModelingSubmission', function($stateParams, ModelingSubmission) {
                    return ModelingSubmission.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'modeling-submission',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('modeling-submission-detail.edit', {
            parent: 'modeling-submission-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/modeling-submission/modeling-submission-dialog.html',
                    controller: 'ModelingSubmissionDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['ModelingSubmission', function(ModelingSubmission) {
                            return ModelingSubmission.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('modeling-submission.new', {
            parent: 'modeling-submission',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/modeling-submission/modeling-submission-dialog.html',
                    controller: 'ModelingSubmissionDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                submissionPath: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('modeling-submission', null, { reload: 'modeling-submission' });
                }, function() {
                    $state.go('modeling-submission');
                });
            }]
        })
        .state('modeling-submission.edit', {
            parent: 'modeling-submission',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/modeling-submission/modeling-submission-dialog.html',
                    controller: 'ModelingSubmissionDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['ModelingSubmission', function(ModelingSubmission) {
                            return ModelingSubmission.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('modeling-submission', null, { reload: 'modeling-submission' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('modeling-submission.delete', {
            parent: 'modeling-submission',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/modeling-submission/modeling-submission-delete-dialog.html',
                    controller: 'ModelingSubmissionDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['ModelingSubmission', function(ModelingSubmission) {
                            return ModelingSubmission.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('modeling-submission', null, { reload: 'modeling-submission' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
