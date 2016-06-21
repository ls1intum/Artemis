(function() {
    'use strict';
    angular
        .module('exerciseApplicationApp')
        .factory('Result', Result);

    Result.$inject = ['$resource', 'DateUtils'];

    function Result ($resource, DateUtils) {
        var resourceUrl =  'api/results/:id';

        return $resource(resourceUrl, {}, {
            'query': { method: 'GET', isArray: true},
            'get': {
                method: 'GET',
                transformResponse: function (data) {
                    if (data) {
                        data = angular.fromJson(data);
                        data.buildCompletionDate = DateUtils.convertDateTimeFromServer(data.buildCompletionDate);
                    }
                    return data;
                }
            },
            'update': { method:'PUT' }
        });
    }
})();
