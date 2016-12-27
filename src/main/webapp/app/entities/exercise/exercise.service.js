(function () {
        'use strict';
        angular
            .module('exerciseApplicationApp')
            .factory('Exercise', Exercise)
            .factory('ExerciseResults', ExerciseResults)
            .factory('ExerciseLtiConfiguration', ExerciseLtiConfiguration)
            .factory('ExerciseParticipations', ExerciseParticipations);

        Exercise.$inject = ['$resource', 'DateUtils'];

        function Exercise($resource, DateUtils) {
            var resourceUrl = 'api/exercises/:id';

            return $resource(resourceUrl, {}, {
                'query': {method: 'GET', isArray: true},
                'reset': {method: 'DELETE', url: 'api/exercises/:id/participations'},
                'get': {
                    method: 'GET',
                    transformResponse: function (data) {
                        if (data) {
                            data = angular.fromJson(data);
                            data.releaseDate = DateUtils.convertDateTimeFromServer(data.releaseDate);
                            data.dueDate = DateUtils.convertDateTimeFromServer(data.dueDate);
                        }
                        return data;
                    }
                },
                'update': {method: 'PUT'}
            });
        }

        ExerciseResults.$inject = ['$resource'];

        function ExerciseResults($resource) {
            var resourceUrl = 'api/courses/:courseId/exercises/:exerciseId/results';

            return $resource(resourceUrl, {}, {
                'query': {method: 'GET', isArray: true}
            });
        }


        ExerciseLtiConfiguration.$inject = ['$resource'];

        function ExerciseLtiConfiguration($resource) {
            var resourceUrl = 'api/lti/configuration/:exerciseId';

            return $resource(resourceUrl, {}, {
                'query': {method: 'GET', isArray: true}
            });
        }


        ExerciseParticipations.$inject = ['$resource'];

        function ExerciseParticipations($resource) {
            var resourceUrl = 'api/exercise/:exerciseId/participations';

            return $resource(resourceUrl, {}, {
                'query': {method: 'GET', isArray: true}
            });


        }
    })();
