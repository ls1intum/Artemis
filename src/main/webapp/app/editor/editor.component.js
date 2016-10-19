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

    EditorController.$inject = ['Participation'];

    function EditorController(Participation) {
        var vm = this;

        console.log(vm.participation);
        console.log(vm.file);

    }
})();
