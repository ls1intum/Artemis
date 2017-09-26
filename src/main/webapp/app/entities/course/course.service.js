(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('Course', Course)
        .factory('CourseExercises', CourseExercises)
        .factory('CourseProgrammingExercises', CourseProgrammingExercises)
        .factory('CourseQuizExercises', CourseQuizExercises)
        .factory('CourseScores', CourseScores);

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
                        if(data.exercise) {
                            var exercise = data.exercise;
                            exercise['participation'] = data;
                            console.log(exercise);
                            return exercise;
                        }
                    }
                    return data;
                },
                ignoreLoadingBar: true
            }
        });
    }

    CourseProgrammingExercises.$inject = ['$resource'];

    function CourseProgrammingExercises ($resource) {
        var resourceUrl = 'api/courses/:courseId/programming-exercises/:exerciseId';

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
                        if(data.exercise) {
                            var exercise = data.exercise;
                            exercise['participation'] = data;
                            console.log(exercise);
                            return exercise;
                        }
                    }
                    return data;
                },
                ignoreLoadingBar: true
            }
        });
    }

    CourseQuizExercises.$inject = ['$resource'];

    function CourseQuizExercises ($resource) {
        var resourceUrl = 'api/courses/:courseId/quiz-exercises/:exerciseId';

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
                        if(data.exercise) {
                            var exercise = data.exercise;
                            exercise['participation'] = data;
                            console.log(exercise);
                            return exercise;
                        }
                    }
                    return data;
                },
                ignoreLoadingBar: true
            }
        });
    }

    CourseScores.$inject = ['$resource'];

    function CourseScores($resource) {
        var resourceUrl =  'api/courses/:courseId/getAllSummedScoresOfCourseUsers';

        return $resource(resourceUrl, {}, {
            'query': { method: 'GET', isArray: true},
            'get': {
                method: 'GET',
                transformResponse: function (data) {
                    if (data) {
                        data = angular.fromJson(data);
                    }
                    return data;
                },
                ignoreLoadingBar: true
            }
        });
    }
})();
