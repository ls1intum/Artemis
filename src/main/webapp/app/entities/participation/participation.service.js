(function() {
    'use strict';
    angular
        .module('exerciseApplicationApp')
        .factory('Participation', Participation)
        .factory('ExerciseParticipation', ExerciseParticipation)
        .factory('CourseParticipation', CourseParticipation);

    Participation.$inject = ['$resource', 'DateUtils'];

    function Participation ($resource, DateUtils) {
        var resourceUrl =  'api/participations/:id';

        return $resource(resourceUrl, {}, {
            'query': { method: 'GET', isArray: true},
            'get': {
                method: 'GET',
                transformResponse: function (data) {
                    if (data) {
                        data = angular.fromJson(data);
                        data.initializationDate = DateUtils.convertDateTimeFromServer(data.initializationDate);
                    }
                    return data;
                }
            },
            'update': { method:'PUT' },
            'repositoryWebUrl': {
                url: 'api/participations/:id/repositoryWebUrl',
                method: 'GET',
                transformResponse: function(data, headersGetter, status) {
                    return {url: data};
                }
            },
            'buildPlanWebUrl': {
                url: 'api/participations/:id/buildPlanWebUrl',
                method: 'GET',
                transformResponse: function(data, headersGetter, status) {
                    return {url: data};
                }
            }
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


    CourseParticipation.$inject = ['$resource'];

    function CourseParticipation ($resource) {
        var resourceUrl = 'api/courses/:courseId/participations';

        return $resource(resourceUrl, {}, {
            'query': {method: 'GET', isArray: true}
        });
    }

})();
