/**
 * Created by Josias Montag on 13/10/16.
 */
(function () {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .component('editor', {
            bindings: {
                participation: '<',
                file: '<',
            },
            templateUrl: 'app/editor/editor.html',
            controller: EditorController
        });

    EditorController.$inject = ['Participation' , 'Repository', '$scope', '$sce'];

    function EditorController(Participation, Repository, $scope, $sce) {
        var vm = this;

        vm.isSaved = true;
        vm.isBuilding = false;

        console.log(vm.participation);
        console.log(vm.file);

        $scope.toggleCollapse = function ($event) {
            $event.toElement.blur();

            var $panel = $($event.toElement).closest('.panel');

            if($panel.hasClass('collapsed')) {
                $panel.removeClass('collapsed');
            } else {
                $panel.addClass('collapsed');
            }

        };


        vm.updateSaveStatusLabel = function ($event) {
            vm.isSaved = $event.isSaved;
            vm.saveStatusLabel = $sce.trustAsHtml($event.saveStatusLabel);
        };

        vm.commit = function ($event) {
            $event.toElement.blur();
            vm.isBuilding = true;
            Repository.commit({
                participationId: vm.participation.id
            }, {}, function () {
                console.log('comitted');
            }, function (err) {
                console.log(err);
            });
        };

    }
})();
