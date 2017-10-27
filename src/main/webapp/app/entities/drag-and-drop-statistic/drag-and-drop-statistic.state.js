(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('drag-and-drop-statistic', {
            parent: 'entity',
            url: '/drag-and-drop-statistic',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'arTeMiSApp.dragAndDropStatistic.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/drag-and-drop-statistic/drag-and-drop-statistics.html',
                    controller: 'DragAndDropStatisticController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('dragAndDropStatistic');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('drag-and-drop-statistic-detail', {
            parent: 'drag-and-drop-statistic',
            url: '/drag-and-drop-statistic/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'arTeMiSApp.dragAndDropStatistic.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/drag-and-drop-statistic/drag-and-drop-statistic-detail.html',
                    controller: 'DragAndDropStatisticDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('dragAndDropStatistic');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'DragAndDropStatistic', function($stateParams, DragAndDropStatistic) {
                    return DragAndDropStatistic.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'drag-and-drop-statistic',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('drag-and-drop-statistic-detail.edit', {
            parent: 'drag-and-drop-statistic-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-statistic/drag-and-drop-statistic-dialog.html',
                    controller: 'DragAndDropStatisticDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['DragAndDropStatistic', function(DragAndDropStatistic) {
                            return DragAndDropStatistic.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('drag-and-drop-statistic.new', {
            parent: 'drag-and-drop-statistic',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-statistic/drag-and-drop-statistic-dialog.html',
                    controller: 'DragAndDropStatisticDialogController',
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
                    $state.go('drag-and-drop-statistic', null, { reload: 'drag-and-drop-statistic' });
                }, function() {
                    $state.go('drag-and-drop-statistic');
                });
            }]
        })
        .state('drag-and-drop-statistic.edit', {
            parent: 'drag-and-drop-statistic',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-statistic/drag-and-drop-statistic-dialog.html',
                    controller: 'DragAndDropStatisticDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['DragAndDropStatistic', function(DragAndDropStatistic) {
                            return DragAndDropStatistic.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('drag-and-drop-statistic', null, { reload: 'drag-and-drop-statistic' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('drag-and-drop-statistic.delete', {
            parent: 'drag-and-drop-statistic',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-statistic/drag-and-drop-statistic-delete-dialog.html',
                    controller: 'DragAndDropStatisticDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['DragAndDropStatistic', function(DragAndDropStatistic) {
                            return DragAndDropStatistic.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('drag-and-drop-statistic', null, { reload: 'drag-and-drop-statistic' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
