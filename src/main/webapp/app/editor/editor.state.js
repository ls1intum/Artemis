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
                url: '/editor/{participationId}',
                contentContainerClass: 'editor',
                bodyClass: 'sidebar-mini',
                data: {
                    authorities: ['ROLE_USER'],
                    pageTitle: 'exerciseApplicationApp.editor.title'
                },
                views: {
                    'content@': {
                        template: '<editor participation="participation"></editor>',
                        controller: ['$scope', 'participation', function ($scope, participation) {
                            $scope.participation = participation;
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
