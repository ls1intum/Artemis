(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DropLocationCounterDialogController', DropLocationCounterDialogController);

    DropLocationCounterDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', '$q', 'entity', 'DropLocationCounter', 'DragAndDropQuestionStatistic', 'DropLocation'];

    function DropLocationCounterDialogController ($timeout, $scope, $stateParams, $uibModalInstance, $q, entity, DropLocationCounter, DragAndDropQuestionStatistic, DropLocation) {
        var vm = this;

        vm.dropLocationCounter = entity;
        vm.clear = clear;
        vm.save = save;
        vm.draganddropquestionstatistics = DragAndDropQuestionStatistic.query();
        vm.droplocations = DropLocation.query({filter: 'droplocationcounter-is-null'});
        $q.all([vm.dropLocationCounter.$promise, vm.droplocations.$promise]).then(function() {
            if (!vm.dropLocationCounter.dropLocation || !vm.dropLocationCounter.dropLocation.id) {
                return $q.reject();
            }
            return DropLocation.get({id : vm.dropLocationCounter.dropLocation.id}).$promise;
        }).then(function(dropLocation) {
            vm.droplocations.push(dropLocation);
        });

        $timeout(function (){
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function save () {
            vm.isSaving = true;
            if (vm.dropLocationCounter.id !== null) {
                DropLocationCounter.update(vm.dropLocationCounter, onSaveSuccess, onSaveError);
            } else {
                DropLocationCounter.save(vm.dropLocationCounter, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('arTeMiSApp:dropLocationCounterUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
