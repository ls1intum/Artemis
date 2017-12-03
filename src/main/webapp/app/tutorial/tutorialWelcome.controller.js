(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('tutorialWelcomeController', tutorialWelcomeController);

    tutorialWelcomeController.$inject = ['$scope', '$uibModalInstance'];

    function tutorialWelcomeController($scope, $uibModalInstance) {
        var vm = this;

        vm.close = function () {
           $uibModalInstance.close({result: 'false'});
        }

        vm.ok = function () {
            $uibModalInstance.close({result: 'ok'});
        }

    }

})();
