(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('MultipleChoiceQuestionStatistic', MultipleChoiceQuestionStatistic)
        .factory('MultipleChoiceQuestionStatisticForStudent', MultipleChoiceQuestionStatisticForStudent);

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

    MultipleChoiceQuestionStatisticForStudent.$inject = ['$resource'];

    function MultipleChoiceQuestionStatisticForStudent ($resource) {
        var resourceUrl =  'api/multiple-choice-question-statistics/:id/for-student';

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
