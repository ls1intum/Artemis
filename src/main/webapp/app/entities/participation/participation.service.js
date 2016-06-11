(function() {
    'use strict';
    angular
        .module('exerciseApplicationApp')
        .factory('Participation', Participation)
        .factory('ExerciseParticipation', ExerciseParticipation);

    Participation.$inject = ['$resource'];

    function Participation ($resource) {
        var resourceUrl =  'api/participations/:id';

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

    ExerciseParticipation.$inject = ['$resource'];

    function ExerciseParticipation ($resource) {
        var resourceUrl = 'api/courses/:courseId/exercises/:exerciseId/participation';

        return $resource(resourceUrl, {}, {
            'query': { method: 'GET' },
            'get': {
                method: 'GET',
                transformResponse: function (data) {
                    if (data) {
                        data = angular.fromJson(data);
                    }
                    return data;
                }
            }
        });
    }
})();
