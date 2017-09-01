(function () {
    'use strict';

    angular
        .module('artemisApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
            .state('base', {
                abstract: true,
                resolve: {
                    authorize: ['Auth',
                        function (Auth) {
                            return Auth.authorize();
                        }
                    ],
                    translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                        $translatePartialLoader.addPart('global');
                    }]
                }
            })
            .state('app', {
                abstract: true,
                parent: 'base',
                views: {
                    'navbar@': {
                        templateUrl: 'app/layouts/navbar/navbar.html',
                        controller: 'NavbarController',
                        controllerAs: 'vm'
                    },
                    'footer@': {
                        templateUrl: 'app/layouts/footer.html'
                    }
                }
            });
    }
})();
