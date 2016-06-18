(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('instructor-dashboard', {
            parent: 'app',
            url: '/course/{courseId}/exercise/{exerciseId}/dashboard',
            data: {
                authorities: ['ROLE_ADMIN'],
                pageTitle: 'exerciseApplicationApp.exercise.home.title'
            },
            views: {
                'content@': {
                    template: '<instructor-dashboard course-id="courseId" exercise-id="exerciseId"></instructor-dashboard>',
                    controller: ['$scope', '$state', function ($scope, $state) {
                        $scope.courseId = $state.params.courseId;
                        $scope.exerciseId = $state.params.exerciseId;
                    }]
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    // TODO: Create partial for instructor dashboard
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        });
    }

})();
