(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('MultipleChoiceQuestionStatistic', MultipleChoiceQuestionStatistic);

    MultipleChoiceQuestionStatistic.$inject = ['$resource'];

    function MultipleChoiceQuestionStatistic ($resource) {
        var resourceUrl =  'api/multiple-choice-question-statistics/:id';

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
