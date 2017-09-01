(function () {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
            .state('courses', {
                parent: 'app',
                url: '/courses',
                data: {
                    authorities: []
                },
                views: {
                    'content@': {
                        templateUrl: 'app/courses/courses.html',
                        controller: 'CoursesController',
                        controllerAs: 'vm'
                    }
                },
                resolve: {
                    mainTranslatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                        $translatePartialLoader.addPart('home');
                        return $translate.refresh();
                    }]
                }
            })
            .state('courses-filtered', {
                parent: 'app',
                url: '/courses/{courseId}/exercise/{exerciseId}',
                data: {
                    authorities: []
                },
                views: {
                    'content@': {
                        templateUrl: 'app/courses/courses.html',
                        controller: 'CoursesController',
                        controllerAs: 'vm'
                    }
                },
                resolve: {
                    mainTranslatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                        $translatePartialLoader.addPart('home');
                        return $translate.refresh();
                    }]
                }
            });
    }
})();
