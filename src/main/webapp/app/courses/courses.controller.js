(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('CoursesController', CoursesController);

    CoursesController.$inject = ['$scope', '$q', '$state', 'Course', 'CourseExercises', 'tutorialWelcomeService', 'Cookie'];

    function CoursesController($scope, $q, $state, Course, CourseExercises, tutorialWelcomeService, Cookie) {
        var vm = this;

        vm.filterByCourseId = _.toInteger(_.get($state,"params.courseId"));
        vm.filterByExerciseId = _.toInteger(_.get($state,"params.exerciseId"));

        loadAll();
        showOverlay();

        function showOverlay() {
            if(Cookie.getFromCookie("tutorialDone") != "true") {
                Cookie.setInCookie("tutorialDone", true, 365);
                tutorialWelcomeService.open();
            }
        }


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
