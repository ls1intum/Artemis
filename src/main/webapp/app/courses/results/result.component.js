/**
 * Created by muenchdo on 11/06/16.
 */
(function () {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .component('result', {
            bindings: {
                participation: '<',
                onNewResult: '&',
            },
            templateUrl: 'app/courses/results/result.html',
            controller: ResultController
        });

    ResultController.$inject = ['$http', '$uibModal', 'ParticipationResult', '$interval','$scope', 'JhiWebsocketService'];

    function ResultController($http, $uibModal, ParticipationResult, $interval,$scope, JhiWebsocketService) {
        var vm = this;

        vm.$onInit = init;
        vm.buildResultString = buildResultString;
        vm.hasResults = hasResults;
        vm.showDetails = showDetails;

        function init() {
            refresh();


            var websocketChannel = '/topic/participation/' + vm.participation.id + '/newResults';

            JhiWebsocketService.subscribe(websocketChannel);

            JhiWebsocketService.receive(websocketChannel).then(null, null, function(notify) {
                refresh();
            });

            $scope.$on('$destroy', function() {
                JhiWebsocketService.unsubscribe(websocketChannel);
            })

        }

        function refresh() {
            $http.get('api/courses/' + vm.participation.exercise.course.id + '/exercises/' + vm.participation.exercise.id + '/participation/status', {
                ignoreLoadingBar: true
            }).then(function (response) {
                vm.queued = response.data === 'QUEUED';
                vm.building = response.data === 'BUILDING';
            }).finally(function () {
                if (!vm.queued && !vm.building) {
                    vm.results = ParticipationResult.query({
                        courseId: vm.participation.exercise.course.id,
                        exerciseId: vm.participation.exercise.id,
                        participationId: vm.participation.id,
                        showAllResults: false
                    }, function (results) {
                        if(vm.onNewResult) {
                            vm.onNewResult(results[0]);
                        }
                    });
                }
            });
        }


        function buildResultString(result) {
            if (result.resultString === 'No tests found') {
                return 'No tests found (Check for compile errors)';
            } else {
                return result.resultString;
            }
        }

        function hasResults() {
            return !!vm.results && vm.results.length > 0;
        }

        function showDetails(result) {
            $uibModal.open({
                size: 'lg',
                templateUrl: 'app/courses/results/result-detail.html',
                controller: ['$http', 'result', function ($http, result) {
                    var vm = this;

                    vm.$onInit = init;

                    function init() {
                        vm.loading = true;
                        $http.get('api/results/' + result.id + '/details').then(function (response) {
                            vm.details = response.data;
                            console.log(response);
                        }).finally(function () {
                            vm.loading = false;
                        });

                    }
                }],
                resolve: {
                    result: result
                },
                controllerAs: '$ctrl'
            });
        }
    }
})();
