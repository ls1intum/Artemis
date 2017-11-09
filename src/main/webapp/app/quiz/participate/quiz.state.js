(function () {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
            .state('quiz-participate', {
                parent: 'app',
                url: '/quiz/{id}',
                data: {
                    authorities: []
                },
                views: {
                    'content@': {
                        templateUrl: 'app/quiz/participate/quiz.html',
                        controller: 'QuizController',
                        controllerAs: 'vm'
                    }
                },
                resolve: {
                    mainTranslatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                        $translatePartialLoader.addPart('quizExercise');
                        $translatePartialLoader.addPart('exercise');
                        $translatePartialLoader.addPart('global');
                        return $translate.refresh();
                    }]
                }
            })
    }
})();
