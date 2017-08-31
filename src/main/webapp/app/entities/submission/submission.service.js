(function() {
    'use strict';
    angular
        .module('exerciseApplicationApp')
        .factory('Submission', Submission);

    Submission.$inject = ['$resource'];

    function Submission ($resource) {
        var resourceUrl =  'api/submissions/:id';

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
