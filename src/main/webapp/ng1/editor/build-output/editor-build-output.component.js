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
            templateUrl: 'ng1/editor/build-output/editor-build-output.html',
            controller: EditorBuildOutputController
        });

    EditorBuildOutputController.$inject = ['Participation', 'Repository', 'ParticipationResult', 'Result', '$scope' ,'$sce'];

    function EditorBuildOutputController(Participation, Repository, ParticipationResult, Result, $scope, $sce) {
        var vm = this;

        vm.buildLogs = [];

        vm.$onInit = function() {

        };

        vm.$onChanges = function(changes) {
            if ((changes.participation && vm.participation) || (changes.isBuilding && changes.isBuilding.currentValue === false && vm.participation)) {
                if (!vm.participation.results) {
                    ParticipationResult.query({
                        courseId: vm.participation.exercise.course.id,
                        exerciseId: vm.participation.exercise.id,
                        participationId: vm.participation.id,
                        showAllResults: false,
                        ratedOnly: vm.participation.exercise.type === "quiz"
                    }).$promise.then(function (results) {
                        toggleBuildLogs(results);
                    });
                } else {
                    toggleBuildLogs(vm.participation.results);
                }
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

        function toggleBuildLogs(results) {
            if (results && results[0]) {
                Result.details({
                    id: results[0].id
                }, function (details) {
                    if (details.length == 0) {
                        getBuildLogs();
                    } else {
                        vm.buildLogs = [];
                    }
                });
            }
        }
    }
})();
