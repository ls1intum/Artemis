(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('MultipleChoiceStatistic', MultipleChoiceStatistic);

    MultipleChoiceStatistic.$inject = ['$resource'];

    function MultipleChoiceStatistic ($resource) {
        var resourceUrl =  'api/multiple-choice-statistics/:id';

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
