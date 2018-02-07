(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('HomeController', HomeController);

    HomeController.$inject = ['$scope', 'Principal', 'LoginService', '$state', '$location'];

    function HomeController ($scope, Principal, LoginService, $state, $location) {
        var vm = this;

        vm.account = null;
        vm.isAuthenticated = null;
        vm.login = LoginService.open;
        vm.register = register;
        $scope.$on('authenticationSuccess', function() {
            getAccount();
        });

        getAccount();

        function getAccount() {
            Principal.identity().then(function(account) {
                vm.account = account;
                vm.isAuthenticated = Principal.isAuthenticated;

                //login directly to the Dashboard In order to access the new WelcomePage
                if (account != null) {
                    $location.path('courses');
                }
            });
        }
        function register () {
            $state.go('register');
        }
    }
})();
