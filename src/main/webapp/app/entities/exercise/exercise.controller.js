(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ExerciseController', ExerciseController);

    ExerciseController.$inject = ['$scope', '$state', 'Exercise', 'ParseLinks', 'AlertService', 'CourseExercises', 'courseEntity', 'paginationConstants'];

    function ExerciseController ($scope, $state, Exercise, ParseLinks, AlertService, CourseExercises, courseEntity, paginationConstants) {
        var vm = this;

        vm.exercises = [];
        vm.loadPage = loadPage;
        vm.itemsPerPage = paginationConstants.itemsPerPage;
        vm.page = 0;
        vm.links = {
            last: 0
        };
        vm.predicate = 'id';
        vm.reset = reset;
        vm.reverse = true;
        vm.course = courseEntity;


        function load() {
            if(vm.course) {
                loadForCourse(vm.course);
            } else {
                loadAll();
            }
        }

        load();


        function loadAll () {
            Exercise.query({
                page: vm.page,
                size: vm.itemsPerPage,
                sort: sort()
            }, onSuccess, onError);
            function sort() {
                var result = [vm.predicate + ',' + (vm.reverse ? 'asc' : 'desc')];
                if (vm.predicate !== 'id') {
                    result.push('id');
                }
                return result;
            }

            function onSuccess(data, headers) {
                vm.links = ParseLinks.parse(headers('link'));
                vm.totalItems = headers('X-Total-Count');
                for (var i = 0; i < data.length; i++) {
                    vm.exercises.push(data[i]);
                }
                getUniqueCourses();
            }

            function onError(error) {
                AlertService.error(error.data.message);
            }
        }

        function loadForCourse (course) {
            CourseExercises.query({
                page: vm.page,
                size: 20,
                courseId: course.id,
                sort: sort()
            }, onSuccess, onError);
            function sort() {
                var result = [vm.predicate + ',' + (vm.reverse ? 'asc' : 'desc')];
                if (vm.predicate !== 'id') {
                    result.push('id');
                }
                return result;
            }
            function onSuccess(data, headers) {
                vm.links = ParseLinks.parse(headers('link'));
                vm.totalItems = headers('X-Total-Count');
                for (var i = 0; i < data.length; i++) {
                    vm.exercises.push(data[i]);
                }
            }
            function onError(error) {
                AlertService.error(error.data.message);
            }
        }


        function getUniqueCourses() {
            var courses = _.map(vm.exercises, function (exercise) {
                return exercise.course;
            });
            vm.courses = _.uniqBy(courses, 'title');
        }

        function reset () {
            vm.page = 0;
            vm.exercises = [];
            load();
        }

        function loadPage(page) {
            vm.page = page;
            load();
        }
    }
})();
