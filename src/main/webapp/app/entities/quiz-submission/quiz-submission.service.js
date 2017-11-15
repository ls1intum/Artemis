(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('QuizSubmission', QuizSubmission)
        .factory('QuizSubmissionForExercise', QuizSubmissionForExercise);

    QuizSubmission.$inject = ['$resource'];

    function QuizSubmission ($resource) {
        var resourceUrl =  'api/quiz-submissions/:id';

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

    QuizSubmissionForExercise.$inject = ['$resource'];

    function QuizSubmissionForExercise ($resource) {
        var resourceUrl = 'api/courses/:courseId/exercises/:exerciseId/submissions/my-latest';

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
