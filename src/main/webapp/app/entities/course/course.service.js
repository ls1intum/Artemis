(function() {
    'use strict';
    angular
        .module('exerciseApplicationApp')
        .factory('Course', Course)
        .factory('CourseExercises', CourseExercises);

    Course.$inject = ['$resource'];

    function Course ($resource) {
        var resourceUrl =  'api/courses/:id';

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

    CourseExercises.$inject = ['$resource'];

    function CourseExercises ($resource) {
        var resourceUrl = 'api/courses/:courseId/exercises/:exerciseId';

        return $resource(resourceUrl, {}, {
            'query': { method: 'GET', isArray: true },
            'get': {
                method: 'GET',
                transformResponse: function (data) {
                    if (data) {
                        data = angular.fromJson(data);
                    }
                    return data;
                }
            },
            'start': {
                url: resourceUrl + '/participations',
                method: 'POST',
                transformResponse: function (data) {
                    if (data) {
                        data = angular.fromJson(data);
                        var exercise = data.exercise;
                        exercise['participation'] = data;
                        console.log(exercise);
                        return exercise;
                    }
                    return data;
                },
                ignoreLoadingBar: true
            }
        });
    }
})();
