(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('drag-and-drop-assignment', {
            parent: 'entity',
            url: '/drag-and-drop-assignment',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'exerciseApplicationApp.dragAndDropAssignment.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/drag-and-drop-assignment/drag-and-drop-assignments.html',
                    controller: 'DragAndDropAssignmentController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('dragAndDropAssignment');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('drag-and-drop-assignment-detail', {
            parent: 'drag-and-drop-assignment',
            url: '/drag-and-drop-assignment/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'exerciseApplicationApp.dragAndDropAssignment.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/drag-and-drop-assignment/drag-and-drop-assignment-detail.html',
                    controller: 'DragAndDropAssignmentDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('dragAndDropAssignment');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'DragAndDropAssignment', function($stateParams, DragAndDropAssignment) {
                    return DragAndDropAssignment.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'drag-and-drop-assignment',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('drag-and-drop-assignment-detail.edit', {
            parent: 'drag-and-drop-assignment-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-assignment/drag-and-drop-assignment-dialog.html',
                    controller: 'DragAndDropAssignmentDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['DragAndDropAssignment', function(DragAndDropAssignment) {
                            return DragAndDropAssignment.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('drag-and-drop-assignment.new', {
            parent: 'drag-and-drop-assignment',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-assignment/drag-and-drop-assignment-dialog.html',
                    controller: 'DragAndDropAssignmentDialogController',
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
                    $state.go('drag-and-drop-assignment', null, { reload: 'drag-and-drop-assignment' });
                }, function() {
                    $state.go('drag-and-drop-assignment');
                });
            }]
        })
        .state('drag-and-drop-assignment.edit', {
            parent: 'drag-and-drop-assignment',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-assignment/drag-and-drop-assignment-dialog.html',
                    controller: 'DragAndDropAssignmentDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['DragAndDropAssignment', function(DragAndDropAssignment) {
                            return DragAndDropAssignment.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('drag-and-drop-assignment', null, { reload: 'drag-and-drop-assignment' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('drag-and-drop-assignment.delete', {
            parent: 'drag-and-drop-assignment',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-assignment/drag-and-drop-assignment-delete-dialog.html',
                    controller: 'DragAndDropAssignmentDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['DragAndDropAssignment', function(DragAndDropAssignment) {
                            return DragAndDropAssignment.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('drag-and-drop-assignment', null, { reload: 'drag-and-drop-assignment' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
