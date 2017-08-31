(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('LtiUserIdDialogController', LtiUserIdDialogController);

    LtiUserIdDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', '$q', 'entity', 'LtiUserId', 'User'];

    function LtiUserIdDialogController ($timeout, $scope, $stateParams, $uibModalInstance, $q, entity, LtiUserId, User) {
        var vm = this;

        vm.ltiUserId = entity;
        vm.clear = clear;
        vm.save = save;
        vm.users = User.query();

        $timeout(function (){
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function save () {
            vm.isSaving = true;
            if (vm.ltiUserId.id !== null) {
                LtiUserId.update(vm.ltiUserId, onSaveSuccess, onSaveError);
            } else {
                LtiUserId.save(vm.ltiUserId, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('exerciseApplicationApp:ltiUserIdUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
