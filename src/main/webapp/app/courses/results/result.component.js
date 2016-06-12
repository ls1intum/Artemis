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

    ResultController.$inject = ['$scope', '$q', '$state', 'ParticipationResult'];

    function ResultController($scope, $q, $state, ParticipationResult) {
        var vm = this;

        vm.$onInit = init;

        function init() {
            console.log(vm.participation);
            vm.results = ParticipationResult.query({
                courseId: vm.participation.exercise.course.id,
                exerciseId: vm.participation.exercise.id,
                participationId: vm.participation.id,
            });
        }
    }
})();
