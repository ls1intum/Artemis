(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('QuizExercise', QuizExercise)
        .factory('QuizExerciseForStudent', QuizExerciseForStudent);

    QuizExercise.$inject = ['$resource'];

    function QuizExercise ($resource) {
        var resourceUrl =  'api/quiz-exercises/:id';

        return $resource(resourceUrl, {}, {
            'query': { method: 'GET', isArray: true},
            'get': {
                method: 'GET',
                transformResponse: function (data) {
                    if (data) {
                        data = angular.fromJson(data);
                    }
                    return data;
                }
            },
            'update': { method:'PUT' }
        });
    }

    QuizExerciseForStudent.$inject = ['$resource'];

    function QuizExerciseForStudent ($resource) {
        var resourceUrl =  'api/quiz-exercises/:id/for-student';

        return $resource(resourceUrl, {}, {
            'get': {
                method: 'GET',
                transformResponse: function (data) {
                    if (data) {
                        data = angular.fromJson(data);
                    }
                    return data;
                }
            }
        });
    }
})();
