(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('LtiUserId', LtiUserId);

    LtiUserId.$inject = ['$resource'];

    function LtiUserId ($resource) {
        var resourceUrl =  'api/lti-user-ids/:id';

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
