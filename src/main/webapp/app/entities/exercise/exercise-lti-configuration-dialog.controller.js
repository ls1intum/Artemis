/**
 * Created by Josias Montag on 26.09.16.
 */
(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ExerciseLtiConfigurationDialogController', ExerciseLtiConfigurationDialogController);

    ExerciseLtiConfigurationDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'exercise', 'configuration', 'ExerciseLtiConfiguration'];

    function ExerciseLtiConfigurationDialogController($timeout, $scope, $stateParams, $uibModalInstance, exercise, configuration, ExerciseLtiConfiguration) {
        var vm = this;

        vm.exercise = exercise;
        vm.configuration = configuration;
        vm.clear = clear;

        function clear() {
            $uibModalInstance.dismiss('cancel');
        }

    }
})();

