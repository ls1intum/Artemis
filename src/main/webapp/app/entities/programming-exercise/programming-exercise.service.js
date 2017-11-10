(function() {
    'use strict';
    angular
        .module('artemisApp')
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
            'save': {
                method: 'POST',
                transformRequest: addType
            },
            'update': {
                method:'PUT',
                transformRequest: addType
            }
        });
    }

    var addType = function(data) {
            data.type = "programming-exercise";
            data = angular.toJson(data);
            return data;
        };

})();
