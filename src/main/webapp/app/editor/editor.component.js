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
                repository: '<',
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
        vm.isCommitted = vm.repository.isClean;
        vm.latestResult = null;


        console.log(vm.participation);
        console.log(vm.file);

        // Collapse parts of the editor (file browser, build output...)
        vm.toggleCollapse = function ($event, horizontal) {

            var target = $event.toElement || $event.relatedTarget || $event.target;

            target.blur();

            var $panel = $(target).closest('.panel');

            if($panel.hasClass('collapsed')) {
                $panel.removeClass('collapsed');
            } else {
                $panel.addClass('collapsed');
                if(horizontal) {
                    $panel.height('35px');
                } else {
                    $panel.width('55px');
                }

            }

        };


        $scope.$on("angular-resizable.resizeEnd", function ($event, args) {
            var $panel = $('#' + args.id);
            $panel.removeClass('collapsed');
        });


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
