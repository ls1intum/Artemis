(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizExerciseController', QuizExerciseController);

    QuizExerciseController.$inject = ['QuizExercise', 'courseEntity', 'CourseQuizExercises'];

    function QuizExerciseController(QuizExercise, courseEntity, CourseQuizExercises) {

        var vm = this;

        vm.quizExercises = [];
        vm.sort = sort;
        vm.predicate = 'id';
        vm.reverse = true;
        vm.course = courseEntity;
        vm.statusForQuiz = statusForQuiz;

        function load() {
            if (vm.course) {
                loadForCourse(vm.course);
            } else {
                loadAll();
            }
        }

        load();

        function loadAll() {
            QuizExercise.query(function(result) {
                vm.quizExercises = result;
                vm.searchQuery = null;
            });
        }

        function loadForCourse(course) {
            CourseQuizExercises.query({
                courseId: course.id
            }, function (result) {
                vm.quizExercises = result;
                vm.searchQuery = null;
            });
        }

        function sort() {
            vm.quizExercises.sort(function (a, b) {
                var result = (a[vm.predicate] < b[vm.predicate]) ? -1 : (a[vm.predicate] > b[vm.predicate]) ? 1 : (
                    (a.id < b.id) ? -1 : (a.id > b.id) ? 1 : 0
                );
                return result * (vm.reverse ? -1 : 1);
            });
        }

        function statusForQuiz(quizExercise) {
            if (quizExercise.isPlannedToStart) {
                var plannedEndMoment = moment(quizExercise.releaseDate).add(quizExercise.duration, "minutes");
                if (plannedEndMoment.isBefore(moment())) {
                    return quizExercise.isOpenForPractice ? "Open for Practice" : "Closed";
                } else if(moment(quizExercise.releaseDate).isBefore(moment())) {
                    return "Active";
                }
            }
            return quizExercise.isVisibleBeforeStart ? "Visible" : "Hidden";
        }
    }
})();
