/**
 * Created by muenchdo on 11/06/16.
 */
(function () {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .component('result', {
            bindings: {
                exercise: '<'
            },
            templateUrl: 'app/courses/exercises/result.html',
            controller: ResultController
        });

    ResultController.$inject = ['$scope', '$q', '$state', 'CourseExercises'];

    function ResultController($scope, $q, $state, CourseExercises) {
        var vm = this;

        vm.resu = [];

        vm.$onInit = init;

        function init() {
            CourseExercises.query({courseId: vm.course.id}).$promise.then(function (exercises) {
                vm.exercises = exercises;
            });
        }
    }
})();
