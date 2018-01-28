(function () {
    'use strict';
    angular
        .module('artemisApp')
        .factory('QuizExercise', QuizExercise)
        .factory('QuizExerciseForStudent', QuizExerciseForStudent)
        .factory('QuizExerciseReEvaluate', QuizExerciseReEvaluate);

    QuizExercise.$inject = ['$resource'];

    function QuizExercise($resource) {
        var resourceUrl = 'api/quiz-exercises/:id';

        return $resource(resourceUrl, {}, {
            'query': {method: 'GET', isArray: true},
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
                method: 'PUT',
                transformRequest: addType
            },
            'start': {
                url: resourceUrl + "/start-now",
                method: 'POST'
            },
            'setVisible': {
                url: resourceUrl + "/set-visible",
                method: 'POST'
            }
        });
    }


    // Type property has to be added to the exercise so that Jackson can
    // deserialize the data into correct concrete implementation of Exercise class
    var addType = function (data) {
        data.type = "quiz-exercise";
        data = angular.toJson(data);
        return data;
    };

    QuizExerciseForStudent.$inject = ['$resource'];

    function QuizExerciseForStudent($resource) {
        var resourceUrl = 'api/quiz-exercises/:id/for-student';

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

    QuizExerciseReEvaluate.$inject = ['$resource'];

    function QuizExerciseReEvaluate ($resource) {
        var resourceUrl =  'api/quiz-exercises-re-evaluate/:id';

        return $resource(resourceUrl, {}, {
            'update': {
                method:'PUT',
                transformRequest: addType
            }
        });
    }
})();
