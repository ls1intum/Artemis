(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('DragItemDialogController', DragItemDialogController);

    DragItemDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', '$q', 'entity', 'DragItem', 'DropLocation', 'DragAndDropQuestion'];

    function DragItemDialogController ($timeout, $scope, $stateParams, $uibModalInstance, $q, entity, DragItem, DropLocation, DragAndDropQuestion) {
        var vm = this;

        vm.dragItem = entity;
        vm.clear = clear;
        vm.save = save;
        vm.correctlocations = DropLocation.query({filter: 'dragitem-is-null'});
        $q.all([vm.dragItem.$promise, vm.correctlocations.$promise]).then(function() {
            if (!vm.dragItem.correctLocation || !vm.dragItem.correctLocation.id) {
                return $q.reject();
            }
            return DropLocation.get({id : vm.dragItem.correctLocation.id}).$promise;
        }).then(function(correctLocation) {
            vm.correctlocations.push(correctLocation);
        });
        vm.draganddropquestions = DragAndDropQuestion.query();

        $timeout(function (){
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function save () {
            vm.isSaving = true;
            if (vm.dragItem.id !== null) {
                DragItem.update(vm.dragItem, onSaveSuccess, onSaveError);
            } else {
                DragItem.save(vm.dragItem, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('exerciseApplicationApp:dragItemUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
