(function () {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {

        $stateProvider
            .state('editor', {
                flushRepositoryCacheAfter: 900000, // 15 min
                participationCache: {},
                repositoryCache: {},
                parent: 'base',
                url: '/editor/{participationId}/{file:any}',
                params: {
                    file: null
                },
                contentContainerClass: 'editor',
                bodyClass: 'editor',
                data: {
                    authorities: ['ROLE_USER'],
                    pageTitle: 'exerciseApplicationApp.editor.title'
                },
                views: {
                    'content@': {
                        template: '<editor participation="participation" file="file" repository="repository"></editor>',
                        controller: ['$scope', 'participation', 'repository', '$state', function ($scope, participation, repository, $state) {
                            $scope.participation = participation;
                            $scope.repository = repository;
                            $scope.file = $state.params.file;
                        }]
                    }
                },
                resolve: {
                    translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                        //$translatePartialLoader.addPart('editor');
                        return $translate.refresh();
                    }],
                    participation: ['$stateParams', 'Participation', '$timeout', function($stateParams, Participation, $timeout) {
                        var state = this;
                        if(!state.participationCache[$stateParams.participationId]) {
                            state.participationCache[$stateParams.participationId] = Participation.get({id : $stateParams.participationId}).$promise;
                            $timeout(function() {
                                delete state.participationCache[$stateParams.participationId];
                            }, state.flushRepositoryCacheAfter);
                        }
                        return state.participationCache[$stateParams.participationId];
                    }],
                    repository: ['$stateParams', 'Repository', '$timeout', function($stateParams, Repository,$timeout) {
                        var state = this;
                        if(!state.repositoryCache[$stateParams.participationId]) {
                            state.repositoryCache[$stateParams.participationId] = Repository.get({participationId : $stateParams.participationId}).$promise;
                            $timeout(function() {
                                delete state.repositoryCache[$stateParams.participationId];
                            }, state.flushRepositoryCacheAfter);
                        }
                        return state.repositoryCache[$stateParams.participationId];
                    }],
                },

            })

    }
})();
