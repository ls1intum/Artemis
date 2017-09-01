(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('DropLocation', DropLocation);

    DropLocation.$inject = ['$resource'];

    function DropLocation ($resource) {
        var resourceUrl =  'api/drop-locations/:id';

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
