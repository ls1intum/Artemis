(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('drag-and-drop-question', {
            parent: 'entity',
            url: '/drag-and-drop-question',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'exerciseApplicationApp.dragAndDropQuestion.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/drag-and-drop-question/drag-and-drop-questions.html',
                    controller: 'DragAndDropQuestionController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('dragAndDropQuestion');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('drag-and-drop-question-detail', {
            parent: 'drag-and-drop-question',
            url: '/drag-and-drop-question/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'exerciseApplicationApp.dragAndDropQuestion.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/drag-and-drop-question/drag-and-drop-question-detail.html',
                    controller: 'DragAndDropQuestionDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('dragAndDropQuestion');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'DragAndDropQuestion', function($stateParams, DragAndDropQuestion) {
                    return DragAndDropQuestion.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'drag-and-drop-question',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('drag-and-drop-question-detail.edit', {
            parent: 'drag-and-drop-question-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-question/drag-and-drop-question-dialog.html',
                    controller: 'DragAndDropQuestionDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['DragAndDropQuestion', function(DragAndDropQuestion) {
                            return DragAndDropQuestion.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('drag-and-drop-question.new', {
            parent: 'drag-and-drop-question',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-question/drag-and-drop-question-dialog.html',
                    controller: 'DragAndDropQuestionDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                backgroundFilePath: null,
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('drag-and-drop-question', null, { reload: 'drag-and-drop-question' });
                }, function() {
                    $state.go('drag-and-drop-question');
                });
            }]
        })
        .state('drag-and-drop-question.edit', {
            parent: 'drag-and-drop-question',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-question/drag-and-drop-question-dialog.html',
                    controller: 'DragAndDropQuestionDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['DragAndDropQuestion', function(DragAndDropQuestion) {
                            return DragAndDropQuestion.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('drag-and-drop-question', null, { reload: 'drag-and-drop-question' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('drag-and-drop-question.delete', {
            parent: 'drag-and-drop-question',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-question/drag-and-drop-question-delete-dialog.html',
                    controller: 'DragAndDropQuestionDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['DragAndDropQuestion', function(DragAndDropQuestion) {
                            return DragAndDropQuestion.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('drag-and-drop-question', null, { reload: 'drag-and-drop-question' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
