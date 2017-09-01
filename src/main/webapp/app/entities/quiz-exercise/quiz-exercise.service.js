(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('QuizExercise', QuizExercise);

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
})();
