(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('DropLocationCounter', DropLocationCounter);

    DropLocationCounter.$inject = ['$resource'];

    function DropLocationCounter ($resource) {
        var resourceUrl =  'api/drop-location-counters/:id';

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
