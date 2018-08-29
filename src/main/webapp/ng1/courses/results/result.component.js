/**
 * Created by muenchdo on 11/06/16.
 */
(function () {
    'use strict';

    angular
        .module('artemisApp')
        .component('result', {
            bindings: {
                isQuiz: '<',
                participation: '<',
                onNewResult: '&'
            },
            templateUrl: 'ng1/courses/results/result.html',
            controller: ResultController
        });

    ResultController.$inject = ['$http', '$uibModal', 'ParticipationResult', 'Repository', '$interval', '$scope', '$sce', 'JhiWebsocketService', 'Result'];

    function ResultController($http, $uibModal, ParticipationResult, Repository, $interval, $scope, $sce, JhiWebsocketService, Result) {
        var vm = this;

        vm.resultString = resultString;
        vm.hasResults = hasResults;
        vm.showDetails = showDetails;
        vm.getTextColorClass = getTextColorClass;
        vm.getResultIconClass = getResultIconClass;
        vm.hasFeedback = hasFeedback;

        vm.$onChanges = function(changes) {
            if (changes.participation && vm.participation) {
                init();
            }
        }

        function init() {

            refresh(false);

            var websocketChannel = '/topic/participation/' + vm.participation.id + '/newResults';

            JhiWebsocketService.subscribe(websocketChannel);

            JhiWebsocketService.receive(websocketChannel).then(null, null, function (notify) {
                refresh(true);
            });

            $scope.$on('$destroy', function () {
                JhiWebsocketService.unsubscribe(websocketChannel);
            })
        }

        /**
         * refresh the participation and load the result if necessary
         *
         * @param forceLoad {boolean} force loading the result if the status is not QUEUED or BUILDING
         */
        function refresh(forceLoad) {
            // TODO: Use WebSocket for participation status in place of GET 'api/participations/{vm.participationId}/status'

            // for now we just ignore participation status, as this is very costly for server performance
            refreshResult(forceLoad);
        }

        function refreshResult(forceLoad) {
            if (forceLoad || !vm.participation.results) {
                // load results from server
                vm.results = ParticipationResult.query({
                    courseId: vm.participation.exercise.course.id,
                    exerciseId: vm.participation.exercise.id,
                    participationId: vm.participation.id,
                    showAllResults: false,
                    ratedOnly: vm.participation.exercise.type === "quiz"
                }, function (results) {
                    if (vm.onNewResult) {
                        vm.onNewResult({
                            $event: {
                                newResult: results[0]
                            }
                        });
                    }
                });
            } else {
                // take results from participation
                angular.forEach(vm.participation.results, function(result) {
                    result.participation = vm.participation;
                });

                vm.results = vm.participation.results;
            }
        }

        function resultString(result) {
            if (result.resultString === 'No tests found') {
                return 'Build failed';
            } else {
                return result.resultString;
            }
        }

        function hasFeedback(result) {
            if (resultString(result) === 'Build failed') {
                return true;
            }
            if (result.hasFeedback === null) {
                return false;
            }
            else return result.hasFeedback;
        }

        function hasResults() {
            return !!vm.results && vm.results.length > 0;
        }

        function showDetails(result) {
            $uibModal.open({
                size: 'lg',
                templateUrl: 'ng1/courses/results/result-detail.html',
                controller: ['$http', 'result', function ($http, result) {
                    var vm = this;

                    vm.$onInit = init;

                    function init() {
                        vm.loading = true;
                        Result.details({
                            id: result.id
                        }, function (details) {
                            vm.details = details;
                            if (details.length == 0) {
                                Repository.buildlogs({
                                    participationId: result.participation.id
                                }, function (buildLogs) {
                                    _.forEach(buildLogs, function (buildLog) {
                                        buildLog.log = $sce.trustAsHtml(buildLog.log);
                                    });
                                    vm.buildLogs = buildLogs;
                                    vm.loading = false;
                                });
                            } else {
                                vm.loading = false;
                            }
                        });
                    }
                }],
                resolve: {
                    result: result
                },
                controllerAs: '$ctrl'
            });
        }

        /**
         * Get the css class for the entire text as a string
         *
         * @return {string} the css class
         */
        function getTextColorClass() {
            var result = vm.results[0];
            if (result.score == null) {
                if (result.successful) {
                    return "text-success";
                } else {
                    return "text-danger";
                }
            } else {
                if (result.score > 80) {
                    return "text-success";
                } else if (result.score > 40) {
                    return "result-orange";
                } else {
                    return "text-danger";
                }
            }
        }

        /**
         * Get the css class for the result icon as a string
         *
         * @return {string} the css class
         */
        function getResultIconClass() {
            var result = vm.results[0];
            if (result.score == null) {
                if (result.successful) {
                    return "fa-check-circle-o";
                } else {
                    return "fa-times-circle-o";
                }
            } else {
                if (result.score > 80) {
                    return "fa-check-circle-o";
                } else {
                    return "fa-times-circle-o";
                }
            }
        }
    }
})();
