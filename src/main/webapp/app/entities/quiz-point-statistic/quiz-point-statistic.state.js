(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('quiz-point-statistic', {
            parent: 'entity',
            url: '/quiz-point-statistic',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'arTeMiSApp.quizPointStatistic.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/quiz-point-statistic/quiz-point-statistics.html',
                    controller: 'QuizPointStatisticController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('quizPointStatistic');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('quiz-point-statistic-detail', {
            parent: 'quiz-point-statistic',
            url: '/quiz-point-statistic/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'arTeMiSApp.quizPointStatistic.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/quiz-point-statistic/quiz-point-statistic-detail.html',
                    controller: 'QuizPointStatisticDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('quizPointStatistic');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'QuizPointStatistic', function($stateParams, QuizPointStatistic) {
                    return QuizPointStatistic.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'quiz-point-statistic',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('quiz-point-statistic-detail.edit', {
            parent: 'quiz-point-statistic-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/quiz-point-statistic/quiz-point-statistic-dialog.html',
                    controller: 'QuizPointStatisticDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['QuizPointStatistic', function(QuizPointStatistic) {
                            return QuizPointStatistic.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('quiz-point-statistic.new', {
            parent: 'quiz-point-statistic',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/quiz-point-statistic/quiz-point-statistic-dialog.html',
                    controller: 'QuizPointStatisticDialogController',
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
                    $state.go('quiz-point-statistic', null, { reload: 'quiz-point-statistic' });
                }, function() {
                    $state.go('quiz-point-statistic');
                });
            }]
        })
        .state('quiz-point-statistic.edit', {
            parent: 'quiz-point-statistic',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/quiz-point-statistic/quiz-point-statistic-dialog.html',
                    controller: 'QuizPointStatisticDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['QuizPointStatistic', function(QuizPointStatistic) {
                            return QuizPointStatistic.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('quiz-point-statistic', null, { reload: 'quiz-point-statistic' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('quiz-point-statistic.delete', {
            parent: 'quiz-point-statistic',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/quiz-point-statistic/quiz-point-statistic-delete-dialog.html',
                    controller: 'QuizPointStatisticDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['QuizPointStatistic', function(QuizPointStatistic) {
                            return QuizPointStatistic.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('quiz-point-statistic', null, { reload: 'quiz-point-statistic' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
