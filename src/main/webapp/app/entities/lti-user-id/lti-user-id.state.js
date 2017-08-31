(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('lti-user-id', {
            parent: 'entity',
            url: '/lti-user-id',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'exerciseApplicationApp.ltiUserId.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/lti-user-id/lti-user-ids.html',
                    controller: 'LtiUserIdController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('ltiUserId');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('lti-user-id-detail', {
            parent: 'lti-user-id',
            url: '/lti-user-id/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'exerciseApplicationApp.ltiUserId.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/lti-user-id/lti-user-id-detail.html',
                    controller: 'LtiUserIdDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('ltiUserId');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'LtiUserId', function($stateParams, LtiUserId) {
                    return LtiUserId.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'lti-user-id',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('lti-user-id-detail.edit', {
            parent: 'lti-user-id-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/lti-user-id/lti-user-id-dialog.html',
                    controller: 'LtiUserIdDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['LtiUserId', function(LtiUserId) {
                            return LtiUserId.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('lti-user-id.new', {
            parent: 'lti-user-id',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/lti-user-id/lti-user-id-dialog.html',
                    controller: 'LtiUserIdDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                ltiUserId: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('lti-user-id', null, { reload: 'lti-user-id' });
                }, function() {
                    $state.go('lti-user-id');
                });
            }]
        })
        .state('lti-user-id.edit', {
            parent: 'lti-user-id',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/lti-user-id/lti-user-id-dialog.html',
                    controller: 'LtiUserIdDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['LtiUserId', function(LtiUserId) {
                            return LtiUserId.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('lti-user-id', null, { reload: 'lti-user-id' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('lti-user-id.delete', {
            parent: 'lti-user-id',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/lti-user-id/lti-user-id-delete-dialog.html',
                    controller: 'LtiUserIdDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['LtiUserId', function(LtiUserId) {
                            return LtiUserId.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('lti-user-id', null, { reload: 'lti-user-id' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
