(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('Course', Course)
        .factory('CourseExercises', CourseExercises)
        .factory('CourseProgrammingExercises', CourseProgrammingExercises)
        .factory('CourseQuizExercises', CourseQuizExercises)
        .factory('CourseScores', CourseScores)
        .factory('CourseTotalScore', CourseTotalScore);


    Course.$inject = ['$resource', 'DateUtils'];

    function Course ($resource, DateUtils) {
        var resourceUrl =  'api/courses/:id';

        return $resource(resourceUrl, {}, {
            'query': { method: 'GET', isArray: true},
            'get': {
                method: 'GET',
                transformResponse: function (data) {
                    if (data) {
                        data = angular.fromJson(data);
                        data.startDate = DateUtils.convertDateTimeFromServer(data.startDate);
                        data.endDate = DateUtils.convertDateTimeFromServer(data.endDate);
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
            },
            'resume': {
                url: resourceUrl + '/resume-participation',
                method: 'PUT',
                transformResponse: function(data) {
                    data = angular.fromJson(data);
                    if(data.exercise) {
                        var exercise = data.exercise;
                        delete(data.exercise);
                        exercise.participation = data;
                        return exercise;
                    }
                    return data;
                }
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

        var resourceUrl =  'api/courses/:courseId/getAllCourseScoresOfCourseUsers';

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

    CourseTotalScore.$inject = ['$resource'];

    function CourseTotalScore($resource) {
        var resourceUrl =  'api/courses/:id/getCourseTotalScoreForUser';

        return $resource(resourceUrl, {}, {
            'query': { method: 'GET'},
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
