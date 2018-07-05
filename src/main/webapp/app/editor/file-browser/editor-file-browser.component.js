/**
 * Created by Josias Montag on 13/10/16.
 */
(function () {
    'use strict';

    angular
        .module('artemisApp')
        .component('editorFileBrowser', {
            bindings: {
                participation: '<',
                file: '=',
                onCreatedFile: '&',
                onDeletedFile: '&',
                repositoryFiles: '<'
            },
            templateUrl: 'ng1/editor/file-browser/editor-file-browser.html',
            controller: EditorFileBrowserController
        });

    EditorFileBrowserController.$inject = ['Participation', 'RepositoryFile', '$state','$uibModal'];

    function EditorFileBrowserController(Participation, RepositoryFile, $state, $uibModal) {
        var vm = this;
        vm.$state = $state;

        vm.$onChanges = function(changes) {
            if (vm.participation && vm.repositoryFiles) {
                init();
            }
        }

        function init() {
            getFiles();
        }


        function getFiles() {
            if (!vm.repositoryFiles) {
                RepositoryFile.query({
                    participationId: vm.participation.id
                }, setupTreeview);
            } else {
                setupTreeview(vm.repositoryFiles);
            }
        }

        vm.getFiles = getFiles;

        function setupTreeview(files) {
            var tree = buildTree(files);
            tree = compressTree(tree);
            $('#fileTree').treeview({
                data: tree,
                levels: 5,
                expandIcon: 'fa fa-folder',
                emptyIcon: 'fa fa-file',
                collapseIcon: 'fa fa-folder-open',
                showBorder: false
            }).on('nodeSelected', function (event, node) {
                vm.file = node.file;
                vm.folder = node.folder;
                /*$state.go('editor', {
                    file: node.file
                }, {notify:false});*/
            });
        }

        function buildTree(files, tree, folder) {
            if (!tree) {
                tree = [];
            }

            _.forEach(files, function (file) {

                // remove leading and trailing slash
                file = file.replace(/^\/|\/$/g, '');

                var fileSplit = file.split('/');

                var node = _.find(tree, {'text': fileSplit[0]});
                if (typeof node == 'undefined') {
                    node = {
                        text: fileSplit[0]
                    };
                    tree.push(node);
                }


                fileSplit.shift();
                if (fileSplit.length > 0) {
                    // directory node
                    node.selectable = false;
                    node.nodes = buildTree([fileSplit.join('/')], node.nodes, folder ? folder + '/' + node.text: node.text);
                    node.folder = node.text;
                } else {
                    // file node
                    node.folder = folder;
                    node.file = (folder ? folder  + '/' : '' )+ node.text;

                    if(node.file == vm.file) {
                        vm.folder = node.folder;
                        node.state = {
                            selected: true
                        }
                    }
                }

            });

            return tree;
        }

        // Compress tree to not contain nodes with only one directory child node
        function compressTree(tree) {

            _.forEach(tree, function (node) {

                if (node.nodes && node.nodes.length == 1 && node.nodes[0].nodes) {
                    node.text = node.text + ' / ' + node.nodes[0].text;
                    node.nodes = compressTree(node.nodes[0].nodes);
                    if(node.nodes[0].nodes) {
                        return compressTree(tree);
                    }
                } else if (node.nodes) {
                    node.nodes = compressTree(node.nodes);
                }
            });


            return tree;
        }


        // Show create file modal
        vm.create = function() {
            $uibModal.open({
                size: 'md',
                templateUrl: 'ng1/editor/file-browser/create-file.html',
                controller: ['$http', '$uibModalInstance', 'fileBrowserComponent', function ($http, $uibModalInstance, fileBrowserComponent) {
                    var vm = this;

                    vm.$onInit = init;
                    vm.folder = fileBrowserComponent.folder;

                    vm.clear = function () {
                        $uibModalInstance.dismiss('cancel');
                    };

                    vm.create = function () {
                        var file = (vm.folder ? vm.folder + "/" : "") + vm.filename;
                        RepositoryFile.create({
                            participationId: fileBrowserComponent.participation.id,
                            file: file
                        }, "" , function () {
                            $uibModalInstance.dismiss();
                            fileBrowserComponent.getFiles();

                            fileBrowserComponent.file = file;

                            /*fileBrowserComponent.$state.go('editor', {
                                file: file
                            }, {notify:true});*/


                            if(fileBrowserComponent.onCreatedFile) {
                                fileBrowserComponent.onCreatedFile({file: file});
                            }

                        }, function (err) {


                        });
                    };

                    function init() {
                        console.log(vm.folder);
                    }
                }],
                resolve: {
                    fileBrowserComponent: function () {
                        return vm;
                    }
                },
                controllerAs: '$ctrl'
            });
        };



        // Show delete file modal
        vm.delete = function() {
            if(!vm.file) {
                return;
            }

            $uibModal.open({
                size: 'md',
                templateUrl: 'ng1/editor/file-browser/delete-file.html',
                controller: ['$http', '$uibModalInstance', 'fileBrowserComponent', function ($http, $uibModalInstance, fileBrowserComponent) {
                    var vm = this;

                    vm.$onInit = init;
                    vm.file = fileBrowserComponent.file;

                    vm.clear = function () {
                        $uibModalInstance.dismiss('cancel');
                    };

                    vm.delete = function () {
                        RepositoryFile.delete({
                            participationId: fileBrowserComponent.participation.id,
                            file: vm.file
                        }, {} , function () {
                            $uibModalInstance.dismiss();
                            fileBrowserComponent.getFiles();

                            fileBrowserComponent.file = null;

                            /*fileBrowserComponent.$state.go('editor', {
                                file: null
                            }, {notify:true});*/

                            if(fileBrowserComponent.onDeletedFile) {
                                fileBrowserComponent.onDeletedFile({file: vm.file});
                            }

                        }, function (err) {

                        });
                    };

                    function init() {
                        console.log(vm.file);
                    }
                }],
                resolve: {
                    fileBrowserComponent: function () {
                        return vm;
                    }
                },
                controllerAs: '$ctrl'
            });
        }



    }
})();
