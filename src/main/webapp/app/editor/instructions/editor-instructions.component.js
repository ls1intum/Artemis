/**
 * Created by Josias Montag on 01/12/16.
 */
(function () {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .component('editorInstructions', {
            bindings: {
                participation: '<',
                latestResult: '<'
            },
            require: {
                editor: '^editor'
            },
            templateUrl: 'app/editor/instructions/editor-instructions.html',
            controller: EditorInstructionsController
        });

    EditorInstructionsController.$inject = ['Participation', 'Repository', 'RepositoryFile', '$scope' ,'$sce', 'Result', '$compile', '$uibModal'];

    function EditorInstructionsController(Participation, Repository, RepositoryFile, $scope, $sce, Result, $compile, $uibModal) {
        var vm = this;

        vm.loading = true;

        vm.initialInstructionsWidth = ($(window).width() - 300) / 2 ;

        vm.$onInit = function () {
            vm.loadReadme();
            vm.md = new Remarkable();
            vm.reg = /^✅\[([^\]]*)\]\s*\(([^)]+)\)/;

            vm.md.inline.ruler.before("text","testsStatus", vm.remarkableParser);
            vm.md.renderer.rules["testsStatus"] = vm.remarkableRenderer;

            //remarkable.renderer.rules[this.id] = this.render.bind(this)

        };


        vm.$onChanges = function (changes) {
            if (changes.latestResult && !vm.loading) {
                vm.loadResultsDetails();
            }
        };




        vm.loadReadme = function () {
            RepositoryFile.get({
                participationId: vm.participation.id,
                file: "README.md"
            }, function (fileObj) {
                vm.readme = fileObj.fileContent;
                vm.renderReadme();
            }, function () {

            });
        };

        vm.loadResultsDetails = function () {
            vm.loading = true;
            Result.details({id : vm.latestResult.id}).$promise.then(function (resultDetails) {
                vm.resultDetails = resultDetails;
                vm.renderReadme();
            });
            vm.loading = false;
        };



        vm.renderReadme = function () {
            vm.loading = true;


            vm.readmeRendered = $compile(vm.md.render(vm.readme))($scope);

            $('.instructions').html(vm.readmeRendered);

            vm.loading = false;
        };



        vm.remarkableParser = function (state, silent) {


            var reg = /^✅\[([^\]]*)\]\s*\(([^)]+)\)/;

            // it is surely not our rule, so we could stop early
            if (state.src[state.pos] !== '✅') return false;

            var match = vm.reg.exec(state.src.slice(state.pos));
            if (!match) return false;

            // in silent mode it shouldn't output any tokens or modify pending
            if (!silent) {
                state.push({
                    type: 'testsStatus',
                    title: match[1],
                    tests: match[2].split(','),
                    level: state.level,
                })
            }

            // every rule should set state.pos to a position after token's contents
            state.pos += match[0].length;

            return true
        };


        vm.remarkableRenderer = function (tokens, id, options, en) {

            var tests = tokens[0].tests;
            var status = vm.statusForTests(tests);

            var text = "<h4 style='margin-bottom: 0px;'>";

            text += status.done ? '<i class="fa fa-lg fa-check-circle-o text-success"></i>' : '<i class="fa fa-lg fa-times-circle-o text-danger"></i>';

            text += ' ' + tokens[0].title;

            text += "</h4> ";

            text += status.done ? ' <span class="text-success" style="margin-left: 28px">' + status.label + '</span>' : '<a style="margin-left: 28px" ng-click="$ctrl.showDetailsForTests($ctrl.latestResult,\'' + tests.toString() + '\')"><span class="text-danger">' + status.label + '</span></a>';

            return text;

        };


        vm.statusForTests = function (tests) {

            var done = false;
            var label = "No results";
            var totalTests = tests.length;

            if(vm.resultDetails) {
                var failedTests = 0;
                _.forEach(tests, function (test) {
                    if(_.some(vm.resultDetails, {'methodName' : test})) {
                        failedTests++;
                    }
                });

                done = failedTests == 0;
                if(totalTests == 1) {
                    if(done) {
                        label = "Test passing";
                    } else {
                        label = "Test failing";
                    }
                } else {
                    if(done) {
                        label = totalTests + " tests passing";
                    } else {
                        label = failedTests + " of " + totalTests + " tests failing";
                    }
                }

            }

            return {
                done: done,
                label: label
            }

        };


        vm.showDetailsForTests = function(result, tests) {
            $uibModal.open({
                size: 'lg',
                templateUrl: 'app/courses/results/result-detail.html',
                controller: ['$http', 'result', 'tests', function ($http, result,tests) {
                    var vm = this;

                    vm.$onInit = init;

                    function init() {
                        vm.loading = true;
                        vm.filterTests = tests;
                        Result.details({
                            id: result.id
                        }, function (details) {
                            vm.details = _.filter(details, function (detail) {
                                return vm.filterTests.indexOf(detail.methodName) != -1;
                            });
                            vm.loading = false;
                        });
                    }
                }],
                resolve: {
                    result: result,
                    tests: function () {
                        return tests.split(',');
                    }
                },
                controllerAs: '$ctrl'
            });
        }




    }
})();
