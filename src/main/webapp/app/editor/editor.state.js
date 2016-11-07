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
                        template: '<editor participation="participation" file="file"></editor>',
                        controller: ['$scope', 'participation', '$state', function ($scope, participation, $state) {
                            $scope.participation = participation;
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
                    }]
                },

            })

    }
})();
