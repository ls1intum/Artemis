/**
 * Created by Josias Montag on 13/10/16.
 */
(function () {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .component('editorFileBrowser', {
            bindings: {
                participation: '<'
            },
            templateUrl: 'app/editor/file-browser/editor-file-browser.html',
            controller: EditorFileBrowserController
        });

    EditorFileBrowserController.$inject = ['Participation'];

    function EditorFileBrowserController(Participation) {
        var vm = this;

        console.log(vm.participation);

        vm.$onInit = init;

        function init() {
            $('#fileTree').treeview({
                data: getTree(),
                showBorder: false
            });
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
            ];;
        }




    }
})();
