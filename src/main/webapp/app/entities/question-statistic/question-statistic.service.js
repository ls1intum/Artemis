(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('QuestionStatistic', QuestionStatistic);

    QuestionStatistic.$inject = ['$resource'];

    function QuestionStatistic ($resource) {
        var resourceUrl =  'api/question-statistics/:id';

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
