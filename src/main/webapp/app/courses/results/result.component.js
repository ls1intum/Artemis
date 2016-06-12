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

    ResultController.$inject = ['ParticipationResult'];

    function ResultController(ParticipationResult) {
        var vm = this;

        vm.$onInit = init;
        vm.hasResults = hasResults;

        function init() {
            vm.results = ParticipationResult.query({
                courseId: vm.participation.exercise.course.id,
                exerciseId: vm.participation.exercise.id,
                participationId: vm.participation.id,
            });
        }

        function hasResults() {
            return !!vm.results && vm.results.length > 0;
        }
    }
})();
