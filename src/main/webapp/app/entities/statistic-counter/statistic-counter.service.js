(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('StatisticCounter', StatisticCounter);

    StatisticCounter.$inject = ['$resource'];

    function StatisticCounter ($resource) {
        var resourceUrl =  'api/statistic-counters/:id';

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
