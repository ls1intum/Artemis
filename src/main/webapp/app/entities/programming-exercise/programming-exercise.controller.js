(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ProgrammingExerciseController', ProgrammingExerciseController);

    ProgrammingExerciseController.$inject = ['ProgrammingExercise', 'courseEntity', 'CourseProgrammingExercises'];

    function ProgrammingExerciseController(ProgrammingExercise, courseEntity, CourseProgrammingExercises) {

        var vm = this;

        vm.programmingExercises = [];
        vm.sort = sort;
        vm.predicate = 'id';
        vm.reverse = true;
        vm.course = courseEntity;

        function load() {
            if (vm.course) {
                loadForCourse(vm.course);
            } else {
                loadAll();
            }
        }

        load();

        function loadAll() {
            ProgrammingExercise.query(function (result) {
                vm.programmingExercises = result;
                vm.searchQuery = null;
            });
        }

        function loadForCourse(course) {
            CourseProgrammingExercises.query({
                courseId: course.id
            }, function (result) {
                vm.programmingExercises = result;
                vm.searchQuery = null;
            });
        }

        function sort() {
            vm.programmingExercises.sort(function (a, b) {
                var result = (a[vm.predicate] < b[vm.predicate]) ? -1 : (a[vm.predicate] > b[vm.predicate]) ? 1 : (
                    (a.id < b.id) ? -1 : (a.id > b.id) ? 1 : 0
                );
                return result * (vm.reverse ? -1 : 1);
            });
        }
    }
})();
