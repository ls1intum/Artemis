(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('drag-and-drop-question-statistic', {
            parent: 'entity',
            url: '/drag-and-drop-question-statistic',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'arTeMiSApp.dragAndDropQuestionStatistic.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/drag-and-drop-question-statistic/drag-and-drop-question-statistics.html',
                    controller: 'DragAndDropQuestionStatisticController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('dragAndDropQuestionStatistic');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('drag-and-drop-question-statistic-detail', {
            parent: 'drag-and-drop-question-statistic',
            url: '/drag-and-drop-question-statistic/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'arTeMiSApp.dragAndDropQuestionStatistic.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/drag-and-drop-question-statistic/drag-and-drop-question-statistic-detail.html',
                    controller: 'DragAndDropQuestionStatisticDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('dragAndDropQuestionStatistic');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'DragAndDropQuestionStatistic', function($stateParams, DragAndDropQuestionStatistic) {
                    return DragAndDropQuestionStatistic.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'drag-and-drop-question-statistic',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('drag-and-drop-question-statistic-detail.edit', {
            parent: 'drag-and-drop-question-statistic-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-question-statistic/drag-and-drop-question-statistic-dialog.html',
                    controller: 'DragAndDropQuestionStatisticDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['DragAndDropQuestionStatistic', function(DragAndDropQuestionStatistic) {
                            return DragAndDropQuestionStatistic.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('drag-and-drop-question-statistic.new', {
            parent: 'drag-and-drop-question-statistic',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-question-statistic/drag-and-drop-question-statistic-dialog.html',
                    controller: 'DragAndDropQuestionStatisticDialogController',
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
                    $state.go('drag-and-drop-question-statistic', null, { reload: 'drag-and-drop-question-statistic' });
                }, function() {
                    $state.go('drag-and-drop-question-statistic');
                });
            }]
        })
        .state('drag-and-drop-question-statistic.edit', {
            parent: 'drag-and-drop-question-statistic',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-question-statistic/drag-and-drop-question-statistic-dialog.html',
                    controller: 'DragAndDropQuestionStatisticDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['DragAndDropQuestionStatistic', function(DragAndDropQuestionStatistic) {
                            return DragAndDropQuestionStatistic.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('drag-and-drop-question-statistic', null, { reload: 'drag-and-drop-question-statistic' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('drag-and-drop-question-statistic.delete', {
            parent: 'drag-and-drop-question-statistic',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-question-statistic/drag-and-drop-question-statistic-delete-dialog.html',
                    controller: 'DragAndDropQuestionStatisticDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['DragAndDropQuestionStatistic', function(DragAndDropQuestionStatistic) {
                            return DragAndDropQuestionStatistic.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('drag-and-drop-question-statistic', null, { reload: 'drag-and-drop-question-statistic' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
