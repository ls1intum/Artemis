(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('multiple-choice-statistic', {
            parent: 'entity',
            url: '/multiple-choice-statistic',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'arTeMiSApp.multipleChoiceStatistic.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/multiple-choice-statistic/multiple-choice-statistics.html',
                    controller: 'MultipleChoiceStatisticController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('multipleChoiceStatistic');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('multiple-choice-statistic-detail', {
            parent: 'multiple-choice-statistic',
            url: '/multiple-choice-statistic/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'arTeMiSApp.multipleChoiceStatistic.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/multiple-choice-statistic/multiple-choice-statistic-detail.html',
                    controller: 'MultipleChoiceStatisticDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('multipleChoiceStatistic');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'MultipleChoiceStatistic', function($stateParams, MultipleChoiceStatistic) {
                    return MultipleChoiceStatistic.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'multiple-choice-statistic',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('multiple-choice-statistic-detail.edit', {
            parent: 'multiple-choice-statistic-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/multiple-choice-statistic/multiple-choice-statistic-dialog.html',
                    controller: 'MultipleChoiceStatisticDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['MultipleChoiceStatistic', function(MultipleChoiceStatistic) {
                            return MultipleChoiceStatistic.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('multiple-choice-statistic.new', {
            parent: 'multiple-choice-statistic',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/multiple-choice-statistic/multiple-choice-statistic-dialog.html',
                    controller: 'MultipleChoiceStatisticDialogController',
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
                    $state.go('multiple-choice-statistic', null, { reload: 'multiple-choice-statistic' });
                }, function() {
                    $state.go('multiple-choice-statistic');
                });
            }]
        })
        .state('multiple-choice-statistic.edit', {
            parent: 'multiple-choice-statistic',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/multiple-choice-statistic/multiple-choice-statistic-dialog.html',
                    controller: 'MultipleChoiceStatisticDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['MultipleChoiceStatistic', function(MultipleChoiceStatistic) {
                            return MultipleChoiceStatistic.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('multiple-choice-statistic', null, { reload: 'multiple-choice-statistic' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('multiple-choice-statistic.delete', {
            parent: 'multiple-choice-statistic',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/multiple-choice-statistic/multiple-choice-statistic-delete-dialog.html',
                    controller: 'MultipleChoiceStatisticDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['MultipleChoiceStatistic', function(MultipleChoiceStatistic) {
                            return MultipleChoiceStatistic.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('multiple-choice-statistic', null, { reload: 'multiple-choice-statistic' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
