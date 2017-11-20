(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
            .state('quiz-statistic-chart', {
                parent: 'app',
                url: '/quiz/{quizId}/quiz-statistic/',
                data: {
                    authorities: []
                },
                views: {
                    'content@': {
                        templateUrl: 'app/statistics/quiz-statistic/show-quiz-statistic.html',
                        controller: 'ShowQuizStatisticController',
                        controllerAs: 'vm'
                    }
                },
                resolve: {
                    mainTranslatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                        $translatePartialLoader.addPart('global');
                        $translatePartialLoader.addPart('showStatistic');
                        return $translate.refresh();
                    }]
                }
            })
            .state('quiz-point-statistic-chart', {
                parent: 'app',
                url: '/quiz/{quizId}/quiz-point-statistic/',
                data: {
                    authorities: []
                },
                views: {
                    'content@': {
                        templateUrl: 'app/statistics/quiz-point-statistic/show-quiz-point-statistic.html',
                        controller: 'ShowQuizPointStatisticController',
                        controllerAs: 'vm'
                    }
                },
                resolve: {
                    mainTranslatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                        $translatePartialLoader.addPart('global');
                        $translatePartialLoader.addPart('showStatistic');
                        return $translate.refresh();
                    }]
                }
            })
            .state('multiple-choice-question-statistic-chart', {
                parent: 'app',
                url: '/quiz/{quizId}/multiple-choice-question-statistic/{questionId}',
                data: {
                    authorities: []
                },
                views: {
                    'content@': {
                        templateUrl: 'app/statistics/multiple-choice-question-statistic/show-multiple-choice-question-statistic.html',
                        controller: 'ShowMultipleChoiceQuestionStatisticController',
                        controllerAs: 'vm'
                    }
                },
                resolve: {
                    mainTranslatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                        $translatePartialLoader.addPart('global');
                        $translatePartialLoader.addPart('showStatistic');
                        return $translate.refresh();
                    }]
                }
            });
        }
})();
