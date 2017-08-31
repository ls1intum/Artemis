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
        vm.steps = [];

        vm.initialInstructionsWidth = ($(window).width() - 300) / 2 ;

        vm.$onInit = function () {
            vm.loadReadme();
            vm.md = new Remarkable();


            vm.md.inline.ruler.before("text","testsStatus", vm.remarkableTestsStatusParser);

            vm.md.block.ruler.before("paragraph","plantUml", vm.remarkablePlantUmlParser);


            vm.md.renderer.rules["testsStatus"] = vm.remarkableTestsStatusRenderer;
            vm.md.renderer.rules["plantUml"] = vm.remarkablePlantUmlRenderer;


        };


        vm.$onChanges = function (changes) {
            if (changes.latestResult && changes.latestResult.currentValue && !vm.loading) {
                // New result available
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

            vm.steps = [];

            vm.readmeRendered = $compile(vm.md.render(vm.readme))($scope);

            $('.instructions').html(vm.readmeRendered);

            vm.loading = false;


            if($('.editor-sidebar-right .panel').height() > $('.editor-sidebar-right').height()) {
                // Safari bug workaround
                $('.editor-sidebar-right .panel').height($('.editor-sidebar-right').height() - 2);
            }


        };


        vm.remarkablePlantUmlParser = function (state, startLine, endLine, silent) {

            var ch, match, nextLine,
                pos = state.bMarks[startLine],
                max = state.eMarks[startLine],
                shift = state.tShift[startLine];

            pos += shift;


            if (shift > 3 || pos + 2 >= max) { return false; }

            if (state.src.charCodeAt(pos) !== 0x40/* @ */) { return false; }

            ch = state.src.charCodeAt(pos + 1);

            if (ch === 0x73) {  // e or s

                // Probably start or end of tag
                if (ch === 0x73/* \ */) {
                    // opening tag
                    match = state.src.slice(pos, max).match(/^@startuml/);
                    if (!match) { return false; }
                }
                if (silent) { return true; }

            } else {
                return false;
            }

            // If we are here - we detected PlantUML block.
            // Let's roll down till empty line (block end).
            nextLine = startLine + 1;
            while (nextLine < state.lineMax && !state.src.slice(state.bMarks[nextLine], state.bMarks[nextLine + 1]).match(/^@enduml/)) {
                nextLine++;
            }


            state.line = nextLine + 1;
            state.tokens.push({
                type: 'plantUml',
                level: state.level,
                lines: [ startLine, state.line ],
                content: state.getLines(startLine, state.line, 0, true)
            });

            return true;


        };

        vm.remarkablePlantUmlRenderer = function (tokens, id, options, en) {

            console.log();
            var plantUml = tokens[id].content;

            plantUml = plantUml.replace("@startuml", "@startuml\nskinparam shadowing false\nskinparam classBorderColor black\nskinparam classArrowColor black\nskinparam DefaultFontSize 14\nskinparam ClassFontStyle bold\nskinparam classAttributeIconSize 0\nhide empty members\n");

            plantUml = plantUml.replace(/testsColor\(([^)]+)\)/g, function (match, capture) {
                var tests = capture.split(",");
                var status = vm.statusForTests(tests);
                return status.done ? "green" : "red";
            });

            return "<img src='/api/plantuml/png?plantuml=" + encodeURIComponent(plantUml) +" '/>";

        };


        vm.remarkableTestsStatusParser = function (state, silent) {


            var reg = /^✅\[([^\]]*)\]\s*\(([^)]+)\)/;

            // it is surely not our rule, so we could stop early
            if (state.src[state.pos] !== '✅') return false;

            var match = reg.exec(state.src.slice(state.pos));
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


        vm.remarkableTestsStatusRenderer = function (tokens, id, options, en) {

            var tests = tokens[0].tests;
            var status = vm.statusForTests(tests);

            var text = "<strong>";

            text += status.done ? '<i class="fa fa-lg fa-check-circle-o text-success" style="font-size: 1.7em;"></i>' : '<i class="fa fa-lg fa-times-circle-o text-danger" style="font-size: 1.7em;"></i>';

            text += ' ' + tokens[0].title;

            text += "</strong>: ";

            text += status.done ? ' <span class="text-success">' + status.label + '</span>' : '<a ng-click="$ctrl.showDetailsForTests($ctrl.latestResult,\'' + tests.toString() + '\')"><span class="text-danger">' + status.label + '</span></a>';

            text += "<br />";

            vm.steps.push({
                title: tokens[0].title,
                done: status.done
            });

            return text;

        };


        vm.statusForTests = function (tests) {

            var done = false;
            var label = "No results";
            var totalTests = tests.length;


            if(vm.resultDetails && vm.resultDetails.length > 0) {
                var failedTests = 0;
                _.forEach(tests, function (test) {
                    if(_.some(vm.resultDetails, {'methodName' : test})) {
                        failedTests++;
                    }
                });

                done = (vm.latestResult && vm.latestResult.successful) || failedTests == 0;
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

            } else if(vm.latestResult && vm.latestResult.successful) {
                done = true;
                label = "Test passing";
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
