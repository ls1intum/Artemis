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
                file: '<'
            },
            templateUrl: 'app/editor/editor.html',
            controller: EditorController
        });

    EditorController.$inject = ['Participation', '$scope'];

    function EditorController(Participation, $scope) {
        var vm = this;

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

    }
})();
