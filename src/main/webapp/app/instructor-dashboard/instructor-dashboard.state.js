(function () {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
            .state('instructor-dashboard', {
                parent: 'app',
                url: '/course/{courseId}/exercise/{exerciseId}/dashboard',
                data: {
                    authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
                    pageTitle: 'artemisApp.exercise.home.title'
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
                        $translatePartialLoader.addPart('global');
                        $translatePartialLoader.addPart('instructorDashboard');
                        $translatePartialLoader.addPart('exercise');
                        $translatePartialLoader.addPart('result');
                        $translatePartialLoader.addPart('feedback');
                        return $translate.refresh();
                    }]
                }
            })
            .state('instructor-course-dashboard', {
                parent: 'app',
                url: '/course/{courseId}/dashboard',
                data: {
                    authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
                    pageTitle: 'artemisApp.exercise.home.title'
                },
                views: {
                    'content@': {
                        template: '<instructor-course-dashboard course-id="courseId"></instructor-course-dashboard>',
                        controller: ['$scope', '$state', function ($scope, $state) {
                            $scope.courseId = $state.params.courseId;
                        }]
                    }
                },
                resolve: {
                    translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                        $translatePartialLoader.addPart('global');
                        return $translate.refresh();
                    }]
                }
            })
            .state('instructor-dashboard.new-result', {
                parent: 'instructor-dashboard',
                url: '/participation/{participationId}/result/new',
                data: {
                    authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA']
                },
                onEnter: ['$stateParams', '$state', '$uibModal', function ($stateParams, $state, $uibModal) {
                    $uibModal.open({
                        templateUrl: 'app/instructor-dashboard/instructor-dashboard-result-dialog.html',
                        controller: 'InstructorDashboardResultDialogController',
                        controllerAs: 'vm',
                        backdrop: 'static',
                        size: 'lg',
                        resolve: {
                            participationEntity: ['Participation', function (Participation) {
                                return Participation.get({id: $stateParams.participationId}).$promise;
                            }],
                            entity: function () {
                                return {
                                    resultString: null,
                                    completionDate: new Date(),
                                    successful: true,
                                    score: 100,
                                    id: null
                                };
                            },
                            translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                                $translatePartialLoader.addPart('error');
                                return $translate.refresh();
                            }]
                        },
                    }).result.then(function () {
                        $state.go('instructor-dashboard', $state.params, {reload: true});
                    }, function () {
                        $state.go('instructor-dashboard', $state.params);
                    });
                }]
            })
            .state('instructor-dashboard.cleanup', {
                parent: 'instructor-dashboard',
                url: '/cleanup',
                data: {
                    authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA']
                },
                onEnter: ['$stateParams', '$state', '$uibModal', function ($stateParams, $state, $uibModal) {
                    $uibModal.open({
                        templateUrl: 'app/instructor-dashboard/instructor-dashboard-cleanup-dialog.html',
                        controller: 'CleanupController',
                        controllerAs: 'vm',
                        size: 'md',
                        resolve: {
                            entity: ['Exercise', function (Exercise) {
                                return Exercise.get({id: $stateParams.exerciseId}).$promise;
                            }]
                        }
                    }).result.then(function () {
                        $state.go('instructor-dashboard');
                    }, function () {
                        $state.go('^');
                    });
                }]
            })
            .state('instructor-dashboard.archive', {
            parent: 'instructor-dashboard',
            url: '/archive',
            data: {
                authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function ($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/instructor-dashboard/instructor-dashboard-archive-dialog.html',
                    controller: 'ArchiveController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['Exercise', function (Exercise) {
                            return Exercise.get({id: $stateParams.exerciseId}).$promise;
                        }]
                    }
                }).result.then(function () {
                    $state.go('instructor-dashboard');
                }, function () {
                    $state.go('^');
                });
            }]
        });
    }

})();
