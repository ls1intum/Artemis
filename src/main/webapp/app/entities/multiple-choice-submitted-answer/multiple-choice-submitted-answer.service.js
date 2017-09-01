(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('MultipleChoiceSubmittedAnswer', MultipleChoiceSubmittedAnswer);

    MultipleChoiceSubmittedAnswer.$inject = ['$resource'];

    function MultipleChoiceSubmittedAnswer ($resource) {
        var resourceUrl =  'api/multiple-choice-submitted-answers/:id';

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
