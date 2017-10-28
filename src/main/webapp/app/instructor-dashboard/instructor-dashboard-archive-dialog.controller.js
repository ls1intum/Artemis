(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ArchiveController', ArchiveController);

    ArchiveController.$inject = ['$uibModalInstance', 'entity', 'Exercise', 'AlertService'];

    function ArchiveController($uibModalInstance, entity, Exercise, AlertService) {
        var vm = this;

        vm.exercise = entity;
        vm.clear = clear;
        vm.confirmArchive = confirmArchive;
        vm.archiveInProgress = false;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmArchive(id) {
            vm.archiveInProgress = true;

            Exercise.archiveExercise({
                    id: id
                },
                function () {
                    $uibModalInstance.close(true);
                    vm.archiveInProgress = false;
                    AlertService.add({
                        type: 'success',
                        msg: 'Archive was successful. The archive zip file with all repositories is currently being downloaded',
                        timeout: 30000
                    });
                }, function () {
                    vm.archiveInProgress = false;
                });
        }
    }
})();
