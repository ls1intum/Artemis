(function () {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('CoursesController', CoursesController);

    CoursesController.$inject = ['$scope', '$q', '$state', 'Course', 'CourseExercises'];

    function CoursesController($scope, $q, $state, Course, CourseExercises) {
        var vm = this;

        loadAll();

        function loadAll() {
            Course.query().$promise.then(function (courses) {
                vm.courses = courses;
            });
        }
    }
})();
