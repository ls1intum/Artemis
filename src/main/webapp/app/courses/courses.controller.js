(function () {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('CoursesController', CoursesController);

    CoursesController.$inject = ['$scope', '$q', '$state', 'Course', 'CourseExercises'];

    function CoursesController($scope, $q, $state, Course, CourseExercises) {
        var vm = this;


        vm.filterByCourseId = _.toInteger(_.get($state,"params.courseId"));
        vm.filterByExerciseId = _.toInteger(_.get($state,"params.exerciseId"));

        loadAll();


        function loadAll() {
            Course.query().$promise.then(function (courses) {

                vm.courses = courses;

                if(vm.filterByCourseId) {
                    vm.courses = _.filter(vm.courses, {
                        'id': vm.filterByCourseId
                    });
                }

            });
        }
    }
})();
