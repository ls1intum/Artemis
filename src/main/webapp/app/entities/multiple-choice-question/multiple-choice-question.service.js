(function() {
    'use strict';
    angular
        .module('exerciseApplicationApp')
        .factory('MultipleChoiceQuestion', MultipleChoiceQuestion);

    MultipleChoiceQuestion.$inject = ['$resource'];

    function MultipleChoiceQuestion ($resource) {
        var resourceUrl =  'api/multiple-choice-questions/:id';

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
