(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('Statistic', Statistic);

    Statistic.$inject = ['$resource'];

    function Statistic ($resource) {
        var resourceUrl =  'api/statistics/:id';

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
