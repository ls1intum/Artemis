(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('TestStatistic', TestStatistic);

    TestStatistic.$inject = ['$resource'];

    function TestStatistic ($resource) {
        var resourceUrl =  'api/test-statistic';

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
            'plusB':{method: 'POST'},
            'update': { method:'PUT' }
        });
    }
})();
