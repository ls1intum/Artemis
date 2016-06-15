/**
 * Created by muenchdo on 11/06/16.
 */
(function () {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .component('result', {
            bindings: {
                participation: '<'
            },
            templateUrl: 'app/courses/results/result.html',
            controller: ResultController
        });

    ResultController.$inject = ['$uibModal', 'ParticipationResult'];

    function ResultController($uibModal, ParticipationResult) {
        var vm = this;

        vm.$onInit = init;
        vm.hasResults = hasResults;
        vm.showDetails = showDetails;

        function init() {
            vm.results = ParticipationResult.query({
                courseId: vm.participation.exercise.course.id,
                exerciseId: vm.participation.exercise.id,
                participationId: vm.participation.id
            });
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
