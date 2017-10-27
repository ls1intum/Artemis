(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('QuizPointStatistic', QuizPointStatistic);

    QuizPointStatistic.$inject = ['$resource'];

    function QuizPointStatistic ($resource) {
        var resourceUrl =  'api/quiz-point-statistics/:id';

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
