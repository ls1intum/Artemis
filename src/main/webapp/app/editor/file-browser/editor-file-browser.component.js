/**
 * Created by Josias Montag on 13/10/16.
 */
(function () {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .component('editorFileBrowser', {
            bindings: {
                participation: '<',
                file: '='
            },
            templateUrl: 'app/editor/file-browser/editor-file-browser.html',
            controller: EditorFileBrowserController
        });

    EditorFileBrowserController.$inject = ['Participation', 'RepositoryFile', '$state'];

    function EditorFileBrowserController(Participation, RepositoryFile, $state) {
        var vm = this;


        vm.$onInit = init;

        function init() {
            getFiles();
        }


        function getFiles() {
            RepositoryFile.query({
                participationId: vm.participation.id
            }, setupTreeview);
        }

        function setupTreeview(files) {
            var tree = buildTree(files);
            tree = compressTree(tree);
            $('#fileTree').treeview({
                data: tree,
                levels: 5,
                expandIcon: 'glyphicon glyphicon-folder-close',
                emptyIcon: 'glyphicon glyphicon-file',
                collapseIcon: 'glyphicon glyphicon-folder-open',
                showBorder: false
            }).on('nodeSelected', function (event, node) {
                vm.file = node.file;
                $state.go('editor', {
                    file: node.file
                }, {notify:false});
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
                } else {
                    // file node
                    node.file = folder  + '/' + node.text;
                    if(node.file == vm.file) {
                        node.state = {
                            selected: true
                        }
                    }
                }

            });

            return tree;
        }

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


        function getTree() {
            // Some logic to retrieve, or generate tree structure
            return [
                {
                    text: "Parent 1",
                    nodes: [
                        {
                            text: "Child 1",
                            nodes: [
                                {
                                    text: "Grandchild 1"
                                },
                                {
                                    text: "Grandchild 2"
                                }
                            ]
                        },
                        {
                            text: "Child 2"
                        }
                    ]
                },
                {
                    text: "Parent 2"
                },
                {
                    text: "Parent 3"
                },
                {
                    text: "Parent 4"
                },
                {
                    text: "Parent 5"
                }
            ];
            ;
        }


    }
})();
