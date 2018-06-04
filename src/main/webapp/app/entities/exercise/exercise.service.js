(function() {
    'use strict';
    angular.module('artemisApp').factory('Exercise', Exercise).factory('ExerciseResults', ExerciseResults).factory('ExerciseLtiConfiguration', ExerciseLtiConfiguration).factory('ExerciseParticipations', ExerciseParticipations);

    Exercise.$inject = ['$resource', 'DateUtils', 'FileSaver', 'Blob'];

    function Exercise($resource, DateUtils, FileSaver, Blob) {
        var resourceUrl = 'api/exercises/:id';

        return $resource(resourceUrl, {}, {
            'query': {
                method: 'GET',
                isArray: true
            },
            'reset': {
                method: 'DELETE',
                url: resourceUrl + '/participations'
            },
            'get': {
                method: 'GET',
                transformResponse: function(data) {
                    if (data) {
                        data = angular.fromJson(data);
                        data.releaseDate = DateUtils.convertDateTimeFromServer(data.releaseDate);
                        data.dueDate = DateUtils.convertDateTimeFromServer(data.dueDate);
                    }
                    return data;
                }
            },
            'update': {
                method: 'PUT'
            },
            'cleanupExercise': {
                method: 'DELETE',
                url: resourceUrl + '/cleanup',
                responseType: 'blob',
                transformResponse: function(data, headersGetter) {
                    if (data) {
                        var headers = headersGetter()
                        if (headers['filename']) {
                            FileSaver.saveAs(data, headers['filename'])
                        }
                    }
                    return data;
                }
            },
            'archiveExercise': {
                method: 'GET',
                url: resourceUrl + '/archive',
                responseType: 'blob',
                transformResponse: function(data, headersGetter) {
                    if (data) {
                        var headers = headersGetter()
                        FileSaver.saveAs(data, headers['filename'])
                    }
                    return data;
                }
            },
            'exportRepos': {
                method: 'GET',
                url: resourceUrl + '/participations/:studentIds',
                responseType: 'blob',
                transformResponse: function(data, headersGetter) {
                    var headers = headersGetter()
                    if (data && headers['filename']) {
                        FileSaver.saveAs(data, headers['filename'])
                    }
                    return data;
                }
            }
        });
    }

    ExerciseResults.$inject = ['$resource'];

    function ExerciseResults($resource) {
        var resourceUrl = 'api/courses/:courseId/exercises/:exerciseId/results';

        return $resource(resourceUrl, {}, {
            'query': {
                method: 'GET',
                isArray: true
            }
        });
    }

    ExerciseLtiConfiguration.$inject = ['$resource'];

    function ExerciseLtiConfiguration($resource) {
        var resourceUrl = 'api/lti/configuration/:exerciseId';

        return $resource(resourceUrl, {}, {
            'query': {
                method: 'GET',
                isArray: true
            }
        });
    }

    ExerciseParticipations.$inject = ['$resource'];

    function ExerciseParticipations($resource) {
        var resourceUrl = 'api/exercise/:exerciseId/participations';

        return $resource(resourceUrl, {}, {
            'query': {
                method: 'GET',
                isArray: true
            }
        });
    }
})();
