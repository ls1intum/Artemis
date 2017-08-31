(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('LtiUserIdDeleteController',LtiUserIdDeleteController);

    LtiUserIdDeleteController.$inject = ['$uibModalInstance', 'entity', 'LtiUserId'];

    function LtiUserIdDeleteController($uibModalInstance, entity, LtiUserId) {
        var vm = this;

        vm.ltiUserId = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            LtiUserId.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
