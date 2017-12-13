(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('PointCounter', PointCounter);

    PointCounter.$inject = ['$resource'];

    function PointCounter ($resource) {
        var resourceUrl =  'api/point-counters/:id';

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
