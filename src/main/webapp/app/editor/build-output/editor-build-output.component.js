/**
 * Created by Josias Montag on 20/10/16.
 */
(function () {
    'use strict';

    angular
        .module('artemisApp')
        .component('editorBuildOutput', {
            bindings: {
                participation: '<',
                isBuilding: '<',
            },
            require: {
                editor: '^editor'
            },
            templateUrl: 'app/editor/build-output/editor-build-output.html',
            controller: EditorBuildOutputController
        });

    EditorBuildOutputController.$inject = ['Participation', 'Repository', '$scope' ,'$sce'];

    function EditorBuildOutputController(Participation, Repository, $scope, $sce) {
        var vm = this;

        vm.buildLogs = [];

        vm.$onInit = function () {

        };

        vm.$onChanges = function (changes) {
            if (changes.isBuilding && changes.isBuilding.currentValue === false) {
                getBuildLogs();
            }
        };

        function getBuildLogs() {
            Repository.buildlogs({
                participationId: vm.participation.id
            }, function (buildLogs) {
                _.forEach(buildLogs, function (buildLog) {
                   buildLog.log = $sce.trustAsHtml(buildLog.log);
                });
                vm.buildLogs = buildLogs;
                $(".buildoutput").scrollTop($(".buildoutput")[0].scrollHeight);
            });
        }

    }
})();
