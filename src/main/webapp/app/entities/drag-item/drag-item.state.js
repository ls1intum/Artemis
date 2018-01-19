(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('drag-item', {
            parent: 'entity',
            url: '/drag-item',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'artemisApp.dragItem.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/drag-item/drag-items.html',
                    controller: 'DragItemController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('dragItem');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('drag-item-detail', {
            parent: 'drag-item',
            url: '/drag-item/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'artemisApp.dragItem.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/drag-item/drag-item-detail.html',
                    controller: 'DragItemDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('dragItem');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'DragItem', function($stateParams, DragItem) {
                    return DragItem.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'drag-item',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('drag-item-detail.edit', {
            parent: 'drag-item-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-item/drag-item-dialog.html',
                    controller: 'DragItemDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['DragItem', function(DragItem) {
                            return DragItem.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('drag-item.new', {
            parent: 'drag-item',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-item/drag-item-dialog.html',
                    controller: 'DragItemDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                pictureFilePath: null,
                                text: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('drag-item', null, { reload: 'drag-item' });
                }, function() {
                    $state.go('drag-item');
                });
            }]
        })
        .state('drag-item.edit', {
            parent: 'drag-item',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-item/drag-item-dialog.html',
                    controller: 'DragItemDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['DragItem', function(DragItem) {
                            return DragItem.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('drag-item', null, { reload: 'drag-item' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('drag-item.delete', {
            parent: 'drag-item',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-item/drag-item-delete-dialog.html',
                    controller: 'DragItemDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['DragItem', function(DragItem) {
                            return DragItem.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('drag-item', null, { reload: 'drag-item' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
