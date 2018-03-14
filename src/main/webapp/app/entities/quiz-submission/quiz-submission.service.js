(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('QuizSubmission', QuizSubmission);

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
            'update': { method:'PUT' },
            'submitForPractice': {
                url: 'api/courses/:courseId/exercises/:exerciseId/submissions/practice',
                method: 'POST'
            },
            'submitForPreview': {
                url: 'api/courses/:courseId/exercises/:exerciseId/submissions/preview',
                method: 'POST'
            }
        });
    }
})();
