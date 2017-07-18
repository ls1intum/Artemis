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
                file: '<',
                onSaveStatusChange: '&'
            },
            templateUrl: 'app/editor/ace/editor-ace.html',
            controller: EditorAceController
        });

    EditorAceController.$inject = ['Participation', 'RepositoryFile', '$scope' ,'$timeout'];

    function EditorAceController(Participation, RepositoryFile, $scope, $timeout) {
        var vm = this;

        vm.sessions = {};
        vm.isSaved = true; // true = all changes saved, false = unsaved changes

        vm.$onInit = function () {
            updateSaveStatusLabel();
        };

        vm.$onChanges = function (changes) {
            if (changes.file && vm.file) {
                // current file has changed
                loadFile(vm.file);
            }
        };

        // Open the file, given by filename
        // If the file was not opened before, a new ACE EditSession for the file is created
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
                        $timeout(function() {
                            onFileChanged(vm.sessions[file]);
                        });
                    });

                }
                vm.editor.setSession(vm.sessions[file]);
                vm.editor.focus();

            });
        }


        // File content was changed
        // This function throttles the persisting to once every 3s
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

            if(vm.onSaveStatusChange) {
                vm.onSaveStatusChange({$event: {
                    isSaved: vm.isSaved,
                    saveStatusLabel: ' <i class="fa fa-circle-o-notch fa-spin text-info"></i><span class="text-info"> Saving file.</span>'
                }
                });
            }

            RepositoryFile.update({
                participationId: vm.participation.id,
                file: session.file
            }, session.getValue() , function () {
                session.unsavedChanges = false;
                updateSaveStatusLabel();

            }, function (err) {
                if(vm.onSaveStatusChange) {
                    vm.onSaveStatusChange({$event: {
                        isSaved: vm.isSaved,
                        saveStatusLabel: '<i class="fa fa-times-circle text-danger"></i> <span class="text-danger"> Failed to save file.</span>'
                    }
                    });

                }
            });



        }


        function updateSaveStatusLabel() {
            var unsavedFiles = _.filter(vm.sessions, {'unsavedChanges': true}).length;
            if(unsavedFiles > 0) {
                vm.isSaved = false;
                if(vm.onSaveStatusChange) {
                    vm.onSaveStatusChange({$event: {
                            isSaved: vm.isSaved,
                            saveStatusLabel: '<i class="fa fa-circle-o-notch fa-spin text-info"></i> <span class="text-info">Unsaved changes in ' + unsavedFiles + ' files.</span>'
                        }
                    });
                }

            } else {
                vm.isSaved = true;
                if(vm.onSaveStatusChange) {
                    vm.onSaveStatusChange({$event: {
                        isSaved: vm.isSaved,
                        saveStatusLabel: '<i class="fa fa-check-circle text-success"></i> <span class="text-success"> All changes saved.</span>'
                    }
                    });
                }

            }

        }

        $scope.aceLoaded = function(_editor) {
            vm.editor = _editor;
            // Options
            console.log('ACE editor loaded');

            if($('.editor-center .panel').height() == 0) {
                // Safari bug workaround
                $('.editor-center .panel').height($('.editor-center').height() - 2);
                vm.editor.resize();
            }

            if(!vm.editor.getSession().file && vm.file) {
                loadFile(vm.file);
            }

        };



    }
})();
