(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('drag-and-drop-submitted-answer', {
            parent: 'entity',
            url: '/drag-and-drop-submitted-answer',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'exerciseApplicationApp.dragAndDropSubmittedAnswer.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/drag-and-drop-submitted-answer/drag-and-drop-submitted-answers.html',
                    controller: 'DragAndDropSubmittedAnswerController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('dragAndDropSubmittedAnswer');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('drag-and-drop-submitted-answer-detail', {
            parent: 'drag-and-drop-submitted-answer',
            url: '/drag-and-drop-submitted-answer/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'exerciseApplicationApp.dragAndDropSubmittedAnswer.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/drag-and-drop-submitted-answer/drag-and-drop-submitted-answer-detail.html',
                    controller: 'DragAndDropSubmittedAnswerDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('dragAndDropSubmittedAnswer');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'DragAndDropSubmittedAnswer', function($stateParams, DragAndDropSubmittedAnswer) {
                    return DragAndDropSubmittedAnswer.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'drag-and-drop-submitted-answer',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('drag-and-drop-submitted-answer-detail.edit', {
            parent: 'drag-and-drop-submitted-answer-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-submitted-answer/drag-and-drop-submitted-answer-dialog.html',
                    controller: 'DragAndDropSubmittedAnswerDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['DragAndDropSubmittedAnswer', function(DragAndDropSubmittedAnswer) {
                            return DragAndDropSubmittedAnswer.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('drag-and-drop-submitted-answer.new', {
            parent: 'drag-and-drop-submitted-answer',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-submitted-answer/drag-and-drop-submitted-answer-dialog.html',
                    controller: 'DragAndDropSubmittedAnswerDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                id: null
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('drag-and-drop-submitted-answer', null, { reload: 'drag-and-drop-submitted-answer' });
                }, function() {
                    $state.go('drag-and-drop-submitted-answer');
                });
            }]
        })
        .state('drag-and-drop-submitted-answer.edit', {
            parent: 'drag-and-drop-submitted-answer',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-submitted-answer/drag-and-drop-submitted-answer-dialog.html',
                    controller: 'DragAndDropSubmittedAnswerDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['DragAndDropSubmittedAnswer', function(DragAndDropSubmittedAnswer) {
                            return DragAndDropSubmittedAnswer.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('drag-and-drop-submitted-answer', null, { reload: 'drag-and-drop-submitted-answer' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('drag-and-drop-submitted-answer.delete', {
            parent: 'drag-and-drop-submitted-answer',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/drag-and-drop-submitted-answer/drag-and-drop-submitted-answer-delete-dialog.html',
                    controller: 'DragAndDropSubmittedAnswerDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['DragAndDropSubmittedAnswer', function(DragAndDropSubmittedAnswer) {
                            return DragAndDropSubmittedAnswer.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('drag-and-drop-submitted-answer', null, { reload: 'drag-and-drop-submitted-answer' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
