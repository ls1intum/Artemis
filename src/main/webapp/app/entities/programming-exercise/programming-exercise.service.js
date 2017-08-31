(function() {
    'use strict';
    angular
        .module('exerciseApplicationApp')
        .factory('ProgrammingExercise', ProgrammingExercise);

    ProgrammingExercise.$inject = ['$resource'];

    function ProgrammingExercise ($resource) {
        var resourceUrl =  'api/programming-exercises/:id';

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
