(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('AnswerOption', AnswerOption);

    AnswerOption.$inject = ['$resource'];

    function AnswerOption ($resource) {
        var resourceUrl =  'api/answer-options/:id';

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
