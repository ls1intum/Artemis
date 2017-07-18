/**
 * Created by Josias Montag on 13/10/16.
 */
(function () {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .component('umleditor', {
            bindings: {
                participation: '<',
                repository: '<',
                file: '<',
            },
            templateUrl: 'app/uml-editor/umleditor.html',
            controller: UmlEditorController
        });

    UmlEditorController.$inject = ['Participation' , 'Repository', '$scope', '$sce'];

    function UmlEditorController(Participation, Repository, $scope, $sce) {
        var vm = this;

        vm.isSaved = true;
        vm.isBuilding = false;
        vm.isCommitted = vm.repository.isClean;
        vm.latestResult = null;


        console.log(vm.participation);
        console.log(vm.file);



        vm.updateSaveStatusLabel = function ($event) {
            vm.isSaved = $event.isSaved;
            if(!vm.isSaved) {
                vm.isCommitted = false;
            }
            vm.saveStatusLabel = $sce.trustAsHtml($event.saveStatusLabel);
        };

        vm.updateLatestResult = function ($event) {
            vm.isBuilding = false;
            vm.latestResult = $event.newResult;
        };

        vm.commit = function ($event) {

            var target = $event.toElement || $event.relatedTarget || $event.target;

            target.blur();
            vm.isBuilding = true;
            Repository.commit({
                participationId: vm.participation.id
            }, {}, function () {
                vm.isCommitted = true;
                console.log('comitted');
            }, function (err) {
                console.log(err);
            });
        };

    }
})();
