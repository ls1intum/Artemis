(function () {
    'use strict';

    angular.module('artemisApp')
        .factory('tutorialWelcomeService', tutorialWelcomeService);

    tutorialWelcomeService.$inject = ['$uibModal'];

    function tutorialWelcomeService($uibModal) {

        var service = {
            open: open
        };

        var modalInstance = null;
        var resetModal = function () {
            modalInstance = null;
        };

        var modalDefaults = {
            animation: true,
            templateUrl: 'app/tutorial/welcomepage.html',
            controller: 'tutorialWelcomeController',
            controllerAs: 'vm'
        };

        return service;

        function open() {
            if (modalInstance !== null) return;

            modalInstance = $uibModal.open(modalDefaults);

            return modalInstance.result;
        }


    }
})();
