(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('drag-and-drop-mapping', {
            parent: 'entity',
            url: '/drag-and-drop-mapping',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'artemisApp.dragAndDropMapping.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/drag-and-drop-mapping/drag-and-drop-mappings.html',
                    controller: 'DragAndDropMappingController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('dragAndDropMapping');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('drag-and-drop-mapping-detail', {
            parent: 'drag-and-drop-mapping',
            url: '/drag-and-drop-mapping/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'artemisApp.dragAndDropMapping.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/drag-and-drop-mapping/drag-and-drop-mapping-detail.html',
                    controller: 'DragAndDropMappingDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('dragAndDropMapping');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'DragAndDropMapping', function($stateParams, DragAndDropMapping) {
                    return DragAndDropMapping.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'drag-and-drop-mapping',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('drag-and-drop-mapping-detail.edit', {
            parent: 'drag-and-drop-mapping-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-mapping/drag-and-drop-mapping-dialog.html',
                    controller: 'DragAndDropMappingDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['DragAndDropMapping', function(DragAndDropMapping) {
                            return DragAndDropMapping.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('drag-and-drop-mapping.new', {
            parent: 'drag-and-drop-mapping',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-mapping/drag-and-drop-mapping-dialog.html',
                    controller: 'DragAndDropMappingDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                dragItemIndex: null,
                                dropLocationIndex: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('drag-and-drop-mapping', null, { reload: 'drag-and-drop-mapping' });
                }, function() {
                    $state.go('drag-and-drop-mapping');
                });
            }]
        })
        .state('drag-and-drop-mapping.edit', {
            parent: 'drag-and-drop-mapping',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-mapping/drag-and-drop-mapping-dialog.html',
                    controller: 'DragAndDropMappingDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['DragAndDropMapping', function(DragAndDropMapping) {
                            return DragAndDropMapping.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('drag-and-drop-mapping', null, { reload: 'drag-and-drop-mapping' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('drag-and-drop-mapping.delete', {
            parent: 'drag-and-drop-mapping',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-mapping/drag-and-drop-mapping-delete-dialog.html',
                    controller: 'DragAndDropMappingDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['DragAndDropMapping', function(DragAndDropMapping) {
                            return DragAndDropMapping.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('drag-and-drop-mapping', null, { reload: 'drag-and-drop-mapping' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
