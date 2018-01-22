(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('PointCounterDialogController', PointCounterDialogController);

    PointCounterDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'PointCounter', 'QuizPointStatistic'];

    function PointCounterDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, PointCounter, QuizPointStatistic) {
        var vm = this;

        vm.pointCounter = entity;
        vm.clear = clear;
        vm.save = save;
        vm.quizpointstatistics = QuizPointStatistic.query();

        $timeout(function (){
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function save () {
            vm.isSaving = true;
            if (vm.pointCounter.id !== null) {
                PointCounter.update(vm.pointCounter, onSaveSuccess, onSaveError);
            } else {
                PointCounter.save(vm.pointCounter, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('artemisApp:pointCounterUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
