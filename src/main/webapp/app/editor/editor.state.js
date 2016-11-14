(function () {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
            .state('editor', {
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
                    participation: ['$stateParams', 'Participation', function($stateParams, Participation) {
                        return Participation.get({id : $stateParams.participationId}).$promise;
                    }],
                    repository: ['$stateParams', 'Repository', function($stateParams, Repository) {
                        return Repository.get({participationId : $stateParams.participationId}).$promise;
                    }],
                },

            })

    }
})();
