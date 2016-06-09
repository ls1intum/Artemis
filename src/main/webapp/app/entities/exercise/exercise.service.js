(function() {
    'use strict';
    angular
        .module('exerciseApplicationApp')
        .factory('Exercise', Exercise);

    Exercise.$inject = ['$resource', 'DateUtils'];

    function Exercise ($resource, DateUtils) {
        var resourceUrl =  'api/exercises/:id';

        return $resource(resourceUrl, {}, {
            'query': { method: 'GET', isArray: true},
            'get': {
                method: 'GET',
                transformResponse: function (data) {
                    if (data) {
                        data = angular.fromJson(data);
                        data.releaseDate = DateUtils.convertDateTimeFromServer(data.releaseDate);
                        data.dueDate = DateUtils.convertDateTimeFromServer(data.dueDate);
                    }
                    return data;
                }
            },
            'update': { method:'PUT' }
        });
    }
})();
