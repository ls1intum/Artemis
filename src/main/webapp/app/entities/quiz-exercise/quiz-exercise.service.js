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
            'save': {
                method: 'POST',
                transformRequest: addType
            },
            'update': {
                method:'PUT',
                transformRequest: addType
            }
        });
    }

    var addType = function(data) {
            data.type = "quiz-exercise";
            data = angular.toJson(data);
            return data;
        };
})();
