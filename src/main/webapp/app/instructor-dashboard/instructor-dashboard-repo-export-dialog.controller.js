(function() {
    'use strict';

    angular.module('artemisApp').controller('RepoExportController', RepoExportController);

    RepoExportController.$inject = ['$uibModalInstance', '$scope', '$document', "$stateParams",'entity', 'Exercise', 'AlertService'];

    function RepoExportController($uibModalInstance, $scope,$document,stateParams, entity, Exercise, AlertService) {
        var vm = this;
        //vm.exercise = entity;
        vm.clear = clear;
        vm.exportRepos = exportRepos;
        $scope.isDisabled = false;


        function clear() {
            $scope.isDisabled = false;
            $uibModalInstance.dismiss('cancel');
        }
        function exportRepos(id) {
            $scope.isDisabled = true;

            Exercise.exportRepos({id: stateParams.exerciseId, studentIds: $scope.list},function (){
                $uibModalInstance.close(true);
            });
        }



        $scope.autoExpand = function(e) {
            var element = angular.isObject(e)
                ? e.target
                : $document.getElementById(e);

            var scrollHeight = element.scrollHeight; // replace 60 by the sum of padding-top and padding-bottom
            element.style.height = scrollHeight + "px";
        };
    }

})();
