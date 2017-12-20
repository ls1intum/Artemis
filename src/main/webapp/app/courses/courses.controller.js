(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('CoursesController', CoursesController);

    CoursesController.$inject = ['$scope', '$q', '$state', 'Course', 'CourseExercises', 'Modal', 'Cookie'];

    function CoursesController($scope, $q, $state, Course, CourseExercises, Modal, Cookie) {
        var vm = this;

        vm.filterByCourseId = _.toInteger(_.get($state,"params.courseId"));
        vm.filterByExerciseId = _.toInteger(_.get($state,"params.exerciseId"));

        loadAll();
        askForTutorial();

        function askForTutorial() {
            if(Cookie.getFromCookie("tutorialDone") != "true") {
                Cookie.setInCookie("tutorialDone", true, 365);

                var modalOptions = {
                    closeButtonText: 'Skip',
                    actionsButtonText: 'Do Tutorial!',
                    headerText: 'Tutorial?',
                    bodyText: 'Since this is your first time on Artemis, do you wish to take a guided Tutorial?'
                };

                Modal.showModal({}, modalOptions);
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
