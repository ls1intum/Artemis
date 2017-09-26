(function () {
    'use strict';

    angular
        .module('artemisApp')
        .component('instructorCourseDashboard', {
            bindings: {
                courseId: '<'
            },
            controller: InstructorCourseDashboardController,
            templateUrl: 'app/instructor-dashboard/instructor-course-dashboard.html'
        });

    InstructorCourseDashboardController.$inject = ['$window', '$filter', 'moment', '$uibModal','Course', 'CourseResult', 'CourseParticipation', 'CourseScores'];

    function InstructorCourseDashboardController($window, $filter, moment, $uibModal, Course, CourseResult, CourseParticipation, CourseScores) {
        var vm = this;


        vm.$onInit = init;
        vm.sort = sort;
        vm.course = Course.get({id : vm.courseId});
        vm.rows = [];
        vm.numberOfExercises = 0;

        function init() {
            getResults();
        }



        function getResults() {
            vm.results = CourseResult.query({
                courseId: vm.courseId
            }, groupResults);
            vm.participations = CourseParticipation.query({
                courseId: vm.courseId
            }, groupResults);
            vm.courseScores = CourseScores.query({
                courseId: vm.courseId
            }, groupResults);
        }

        function groupResults() {
            if(!vm.results || !vm.participations || !vm.courseScores || vm.participations.length == 0 || vm.results.length == 0 || vm.courseScores.length == 0) {
                return
            }
            var rows = {};
            var exercisesSeen = {};
            _.forEach(vm.participations, function (p) {
               if(!rows[p.student.id]) {
                   rows[p.student.id] = {
                       'firstName': p.student.firstName,
                       'lastName': p.student.lastName,
                       'login': p.student.login,
                       'participated': 0,
                       'participatedPercentage': 0,
                       'successful': 0,
                       'successfulPercentage': 0,
                       'overallScore': 0,
                   }
               }
               rows[p.student.id].participated++;
               if(!exercisesSeen[p.exercise.id]) {
                   exercisesSeen[p.exercise.id] = true;
                   vm.numberOfExercises++;
               }
            });
            _.forEach(vm.results, function (r) {
                rows[r.participation.student.id].successful++;
                rows[r.participation.student.id].successfulPercentage = parseFloat((rows[r.participation.student.id].successful / vm.numberOfExercises)*100);
            });

            var studentSeen = {};
            _.forEach(vm.participations, function (p) {
                if(!studentSeen[p.student.id]) {
                    studentSeen[p.student.id] = true;
                    rows[p.student.id].participatedPercentage = parseFloat((rows[p.student.id].participated / vm.numberOfExercises) * 100);
                }
            });

            _.forEach(vm.courseScores, function (s) {
               rows[s.id].overallScore = parseFloat(s.score / vm.numberOfExercises);
            })


            vm.rows = _.values(rows);

        }



        function sort(item) {
            return item[vm.sortColumn];
        }


    }
})();
