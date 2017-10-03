(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizExerciseDetailController', QuizExerciseDetailController);

    QuizExerciseDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'QuizExercise', 'Question', 'courseEntity'];

    function QuizExerciseDetailController($scope, $rootScope, $stateParams, previousState, entity, QuizExercise, Question, courseEntity) {
        var vm = this;
        var savedEntity = entity.id ? Object.assign({}, entity) : {};

        vm.quizExercise = entity;
        vm.previousState = previousState.name;
        vm.quizExercise.course = courseEntity;
        vm.datePickerOpenStatus = {
            plannedStartDateTime: false
        };
        vm.statusOptions = [
            {
                key: 1,
                label: "Hidden"
            },
            {
                key: 2,
                label: "Visible"
            },
            {
                key: 3,
                label: "Active"
            },
            {
                key: 4,
                label: "Closed"
            },
            {
                key: 5,
                label: "Open for Practice"
            }
        ];
        vm.pendingChanges = pendingChanges;
        vm.validQuiz = validQuiz;
        vm.openCalendar = openCalendar;
        vm.addQuestion = addQuestion;
        vm.goBack = goBack;
        vm.save = save;

        console.log(vm.quizExercise);

        var unsubscribe = $rootScope.$on('artemisApp:quizExerciseUpdate', function (event, result) {
            vm.quizExercise = result;
        });
        $scope.$on('$destroy', unsubscribe);

        function openCalendar(date) {
            vm.datePickerOpenStatus[date] = true;
        }

        function addQuestion() {
            // TODO
            alert("TODO");
        }

        function pendingChanges() {
            return [
                "title",
                "duration",
                "plannedStart",
                "plannedStartDateTime",
                "status",
                "questions"
            ].some(function (key) {
                return vm.quizExercise[key] !== savedEntity[key];
            });
        }

        function validQuiz() {
            if (!vm.quizExercise.title || vm.quizExercise.title === "") {
                return false;
            }
            if (!vm.quizExercise.duration) {
                return false;
            }
            if (vm.quizExercise.plannedStart) {
                switch (vm.quizExercise.status) {
                    case 1:
                    case 2:
                        if (moment(vm.quizExercise.plannedStartDateTime).isBefore(moment())) {
                            return false;
                        }
                        break;
                    case 3:
                    case 4:
                    case 5:
                        if (moment(vm.quizExercise.plannedStartDateTime).isAfter(moment())) {
                            return false;
                        }
                        break;
                }
            }
            return true;
        }

        function goBack() {
            // TODO
            alert("TODO");
        }

        function save() {
            // TODO
            alert("TODO");
        }
    }
})();
