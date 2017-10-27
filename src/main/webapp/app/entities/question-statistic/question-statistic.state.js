(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('question-statistic', {
            parent: 'entity',
            url: '/question-statistic',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'arTeMiSApp.questionStatistic.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/question-statistic/question-statistics.html',
                    controller: 'QuestionStatisticController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('questionStatistic');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('question-statistic-detail', {
            parent: 'question-statistic',
            url: '/question-statistic/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'arTeMiSApp.questionStatistic.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/question-statistic/question-statistic-detail.html',
                    controller: 'QuestionStatisticDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('questionStatistic');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'QuestionStatistic', function($stateParams, QuestionStatistic) {
                    return QuestionStatistic.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'question-statistic',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('question-statistic-detail.edit', {
            parent: 'question-statistic-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/question-statistic/question-statistic-dialog.html',
                    controller: 'QuestionStatisticDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['QuestionStatistic', function(QuestionStatistic) {
                            return QuestionStatistic.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('question-statistic.new', {
            parent: 'question-statistic',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/question-statistic/question-statistic-dialog.html',
                    controller: 'QuestionStatisticDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                ratedCorrectCounter: null,
                                unRatedCorrectCounter: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('question-statistic', null, { reload: 'question-statistic' });
                }, function() {
                    $state.go('question-statistic');
                });
            }]
        })
        .state('question-statistic.edit', {
            parent: 'question-statistic',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/question-statistic/question-statistic-dialog.html',
                    controller: 'QuestionStatisticDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['QuestionStatistic', function(QuestionStatistic) {
                            return QuestionStatistic.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('question-statistic', null, { reload: 'question-statistic' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('question-statistic.delete', {
            parent: 'question-statistic',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/question-statistic/question-statistic-delete-dialog.html',
                    controller: 'QuestionStatisticDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['QuestionStatistic', function(QuestionStatistic) {
                            return QuestionStatistic.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('question-statistic', null, { reload: 'question-statistic' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
