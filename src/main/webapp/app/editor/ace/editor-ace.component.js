/**
 * Created by Josias Montag on 20/10/16.
 */
(function () {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .component('editorAce', {
            bindings: {
                participation: '<',
                file: '<'
            },
            templateUrl: 'app/editor/ace/editor-ace.html',
            controller: EditorAceController
        });

    EditorAceController.$inject = ['Participation', 'RepositoryFile', '$scope'];

    function EditorAceController(Participation, RepositoryFile, $scope) {
        var vm = this;

        vm.sessions = {};


        vm.$onInit = function () {

        };

        vm.$onChanges = function (changes) {
            if (changes.file) {
                loadFile(vm.file);
            }
        };

        function loadFile(file) {
            RepositoryFile.get({
                participationId: vm.participation.id,
                file: file
            }, function (fileObj) {

                if(!vm.sessions[file]) {

                    var modelist = ace.require("ace/ext/modelist");
                    var mode = modelist.getModeForPath(file).mode;


                    vm.sessions[file] = new ace.EditSession(fileObj.fileContent, mode);
                    vm.sessions[file].file = file;
                }
                vm.editor.setSession(vm.sessions[file]);
                vm.editor.focus();

            });
        }


        $scope.aceLoaded = function(_editor) {
            vm.editor = _editor;
            // Options
            console.log('ACE editor loaded');



            if(!vm.editor.getSession().file && vm.file) {
                loadFile(vm.file);
            }

        };

        $scope.aceChanged = function(e) {
            //
            console.log('ACE editor changed');
            console.log(vm.editor.getSession().file);
        };


    }
})();
