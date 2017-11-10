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

    // Type property has to be added to the exercise so that Jackson can
    // deserialize the data into correct concrete implementation of Exercise class
    var addType = function(data) {
            data.type = "modeling-exercise";
            data = angular.toJson(data);
            return data;
        };

})();
