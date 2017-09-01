(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('ModelingExercise', ModelingExercise);

    ModelingExercise.$inject = ['$resource'];

    function ModelingExercise ($resource) {
        var resourceUrl =  'api/modeling-exercises/:id';

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
