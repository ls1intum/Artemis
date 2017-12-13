(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('AnswerCounter', AnswerCounter);

    AnswerCounter.$inject = ['$resource'];

    function AnswerCounter ($resource) {
        var resourceUrl =  'api/answer-counters/:id';

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
