(function() {
    'use strict';
    angular
        .module('exerciseApplicationApp')
        .factory('Participation', Participation);

    Participation.$inject = ['$resource'];

    function Participation ($resource) {
        var resourceUrl =  'api/participations/:id';

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
