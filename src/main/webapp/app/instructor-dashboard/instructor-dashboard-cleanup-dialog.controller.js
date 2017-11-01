(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('CleanupController', CleanupController);

    CleanupController.$inject = ['$uibModalInstance', 'entity', 'Exercise', 'AlertService'];

    function CleanupController($uibModalInstance, entity, Exercise, AlertService) {
        var vm = this;

        vm.exercise = entity;
        vm.clear = clear;
        vm.confirmCleanup = confirmCleanup;
        vm.confirmExerciseName = "";
        vm.deleteRepositories = false;
        vm.cleanupInProgress = false;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmCleanup(id) {
            vm.cleanupInProgress = true;

            Exercise.cleanupExercise({
                    id: id,
                    deleteRepositories: vm.deleteRepositories
                },
                function () {
                    $uibModalInstance.close(true);
                    vm.deleteInProgress = false;
                    if(vm.deleteRepositories) {
                        AlertService.add({
                            type: 'success',
                            msg: 'Cleanup was successful. All build plans and repositories have been deleted. All participations have been marked as Finished. The archive zip file with all repositories is currently being downloaded',
                            timeout: 30000
                        });
                    }
                    else {
                        AlertService.add({
                            type: 'success',
                            msg: 'Cleanup was successful. All build plans have been deleted. Students can now resume their participation',
                            timeout: 30000
                        });
                    }
                }, function () {
                    vm.deleteInProgress = false;
                });
        }
    }
})();
