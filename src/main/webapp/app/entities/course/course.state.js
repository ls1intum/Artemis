(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('course', {
            parent: 'entity',
            url: '/course',
            data: {
                authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
                pageTitle: 'artemisApp.course.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/course/courses.html',
                    controller: 'CourseController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('course');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('course-detail', {
            parent: 'course',
            url: '/course/{id}',
            data: {
                authorities: ['ROLE_ADMIN'],
                pageTitle: 'artemisApp.course.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/course/course-detail.html',
                    controller: 'CourseDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('course');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'Course', function($stateParams, Course) {
                    return Course.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'course',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('course-detail.edit', {
            parent: 'course-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/course/course-dialog.html',
                    controller: 'CourseDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['Course', function(Course) {
                            return Course.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('course.new', {
            parent: 'course',
            url: '/new',
            data: {
                authorities: ['ROLE_ADMIN']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/course/course-dialog.html',
                    controller: 'CourseDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                title: null,
                                studentGroupName: null,
                                teachingAssistantGroupName: null,
                                instructorGroupName: null,
                                startDate: null,
                                endDate: null,
                                onlineCourse: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('course', null, { reload: 'course' });
                }, function() {
                    $state.go('course');
                });
            }]
        })
        .state('course.edit', {
            parent: 'course',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_ADMIN']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/course/course-dialog.html',
                    controller: 'CourseDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['Course', function(Course) {
                            return Course.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('course', null, { reload: 'course' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('course.delete', {
            parent: 'course',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_ADMIN']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/course/course-delete-dialog.html',
                    controller: 'CourseDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['Course', function(Course) {
                            return Course.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('course', null, { reload: 'course' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
