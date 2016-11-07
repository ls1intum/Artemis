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

    EditorAceController.$inject = ['Participation', 'RepositoryFile', '$scope' ,'$timeout'];

    function EditorAceController(Participation, RepositoryFile, $scope, $timeout) {
        var vm = this;

        vm.sessions = {};
        vm.saveStatus = true; // true = all changes saved, false = unsaved changes

        vm.$onInit = function () {
            updateSaveStatusLabel();
        };

        vm.$onChanges = function (changes) {
            if (changes.file && vm.file) {
                loadFile(vm.file);
            }
        };

        function loadFile(file) {
            RepositoryFile.get({
                participationId: vm.participation.id,
                file: file
            }, function (fileObj) {

                if(!vm.sessions[file]) {

                    var ModeList = ace.require("ace/ext/modelist");
                    var mode = ModeList.getModeForPath(file).mode;


                    vm.sessions[file] = new ace.EditSession(fileObj.fileContent, mode);
                    vm.sessions[file].file = file;
                    vm.sessions[file].on("change", function (e) {
                        onFileChanged(vm.sessions[file]);
                    });

                }
                vm.editor.setSession(vm.sessions[file]);
                vm.editor.focus();

            });
        }

        function onFileChanged(session) {
            if(session.saveTimer) {
                $timeout.cancel(session.saveTimer);
            }
            session.unsavedChanges = true;

            session.saveTimer = $timeout(function () {
                saveFile(session);
            }, 3000);
            updateSaveStatusLabel();
        }


        function saveFile(session) {
            console.log('Saving ' + session.file);

            $scope.$emit('saveStatusLabel',' <i class="fa fa-circle-o-notch fa-spin text-info"></i><span class="text-info"> Saving file.</span>');

            RepositoryFile.update({
                participationId: vm.participation.id,
                file: session.file
            }, session.getValue() , function () {
                session.unsavedChanges = false;
                updateSaveStatusLabel();

            }, function (err) {
                $scope.$emit('saveStatusLabel','<i class="fa fa-times-circle text-danger"></i> <span class="text-danger"> Failed to save file.</span>');
            });



        }


        function updateSaveStatusLabel() {
            var unsavedFiles = _.filter(vm.sessions, {'unsavedChanges': true}).length;
            if(unsavedFiles > 0) {
                vm.saveStatus = false;
                $scope.$emit('saveStatusLabel',' <i class="fa fa-warning text-warning"></i> <span class="text-warning">Unsaved changes in ' + unsavedFiles + ' files.</span>');
            } else {
                vm.saveStatus = true;
                $scope.$emit('saveStatusLabel',' <i class="fa fa-check-circle text-success"></i> <span class="text-success"> All changes saved.</span>');
            }
            $scope.$emit('saveStatus', vm.saveStatus);
        }

        $scope.aceLoaded = function(_editor) {
            vm.editor = _editor;
            // Options
            console.log('ACE editor loaded');

            if(!vm.editor.getSession().file && vm.file) {
                loadFile(vm.file);
            }

        };



    }
})();
