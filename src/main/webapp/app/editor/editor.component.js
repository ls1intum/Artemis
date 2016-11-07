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

        vm.saveStatus = true;

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

        }

        $scope.$on('saveStatusLabel', function(event, data) {
            vm.saveStatusLabel = $sce.trustAsHtml(data);
            $scope.$apply();
        });


        $scope.$on('saveStatus', function(event, data) {
            vm.saveStatus = data;
        });

        vm.commit = function () {
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
