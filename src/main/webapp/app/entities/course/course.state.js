(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('course', {
            parent: 'entity',
            url: '/course',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'exerciseApplicationApp.course.home.title'
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
            parent: 'entity',
            url: '/course/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'exerciseApplicationApp.course.detail.title'
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
                }]
            }
        })
        .state('course.new', {
            parent: 'course',
            url: '/new',
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
                        entity: function () {
                            return {
                                title: null,
                                slug: null,
                                studentGroupName: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('course', null, { reload: true });
                }, function() {
                    $state.go('course');
                });
            }]
        })
        .state('course.edit', {
            parent: 'course',
            url: '/{id}/edit',
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
                    $state.go('course', null, { reload: true });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('course.delete', {
            parent: 'course',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
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
                    $state.go('course', null, { reload: true });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
