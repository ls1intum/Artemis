(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('multiple-choice-question-statistic', {
            parent: 'entity',
            url: '/multiple-choice-question-statistic',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'arTeMiSApp.multipleChoiceQuestionStatistic.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/multiple-choice-question-statistic/multiple-choice-question-statistics.html',
                    controller: 'MultipleChoiceQuestionStatisticController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('multipleChoiceQuestionStatistic');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('multiple-choice-question-statistic-detail', {
            parent: 'multiple-choice-question-statistic',
            url: '/multiple-choice-question-statistic/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'arTeMiSApp.multipleChoiceQuestionStatistic.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic-detail.html',
                    controller: 'MultipleChoiceQuestionStatisticDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('multipleChoiceQuestionStatistic');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'MultipleChoiceQuestionStatistic', function($stateParams, MultipleChoiceQuestionStatistic) {
                    return MultipleChoiceQuestionStatistic.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'multiple-choice-question-statistic',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('multiple-choice-question-statistic-detail.edit', {
            parent: 'multiple-choice-question-statistic-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic-dialog.html',
                    controller: 'MultipleChoiceQuestionStatisticDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['MultipleChoiceQuestionStatistic', function(MultipleChoiceQuestionStatistic) {
                            return MultipleChoiceQuestionStatistic.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('multiple-choice-question-statistic.new', {
            parent: 'multiple-choice-question-statistic',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic-dialog.html',
                    controller: 'MultipleChoiceQuestionStatisticDialogController',
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
                    $state.go('multiple-choice-question-statistic', null, { reload: 'multiple-choice-question-statistic' });
                }, function() {
                    $state.go('multiple-choice-question-statistic');
                });
            }]
        })
        .state('multiple-choice-question-statistic.edit', {
            parent: 'multiple-choice-question-statistic',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic-dialog.html',
                    controller: 'MultipleChoiceQuestionStatisticDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['MultipleChoiceQuestionStatistic', function(MultipleChoiceQuestionStatistic) {
                            return MultipleChoiceQuestionStatistic.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('multiple-choice-question-statistic', null, { reload: 'multiple-choice-question-statistic' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('multiple-choice-question-statistic.delete', {
            parent: 'multiple-choice-question-statistic',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic-delete-dialog.html',
                    controller: 'MultipleChoiceQuestionStatisticDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['MultipleChoiceQuestionStatistic', function(MultipleChoiceQuestionStatistic) {
                            return MultipleChoiceQuestionStatistic.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('multiple-choice-question-statistic', null, { reload: 'multiple-choice-question-statistic' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
