(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('ModelingSubmission', ModelingSubmission);

    ModelingSubmission.$inject = ['$resource'];

    function ModelingSubmission ($resource) {
        var resourceUrl =  'api/modeling-submissions/:id';

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
