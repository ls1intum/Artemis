(function () {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ParticipationDeleteController', ParticipationDeleteController);

    ParticipationDeleteController.$inject = ['$uibModalInstance', 'entity', 'Participation'];

    function ParticipationDeleteController($uibModalInstance, entity, Participation) {
        var vm = this;

        vm.participation = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear() {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete(id, deleteBuildPlan, deleteRepository) {
            Participation.delete({
                    id: id,
                    deleteBuildPlan: deleteBuildPlan,
                    deleteRepository: deleteRepository
                },
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
