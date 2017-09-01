(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('SubmittedAnswer', SubmittedAnswer);

    SubmittedAnswer.$inject = ['$resource'];

    function SubmittedAnswer ($resource) {
        var resourceUrl =  'api/submitted-answers/:id';

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
