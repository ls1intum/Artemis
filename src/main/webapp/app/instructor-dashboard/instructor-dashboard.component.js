(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .component('instructorDashboard', {
            bindings: {
                courseId: '<',
                exerciseId: '<'
            },
            controller: InstructorDashboardController,
            templateUrl: 'app/instructor-dashboard/instructor-dashboard.html'
        });

    InstructorDashboardController.$inject = ['ExerciseResults'];

    function InstructorDashboardController (ExerciseResults) {
        var vm = this;

        vm.$onInit = init;
        vm.refresh = getResults;

        function init() {
            getResults();
        }

        function getResults() {
            vm.results = ExerciseResults.query({
                courseId: vm.courseId,
                exerciseId: vm.exerciseId
            });
        }
    }
})();
