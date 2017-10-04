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
            releaseDate: false
        };
        vm.isSaving = false;
        vm.true = true;
        vm.statusOptionsVisible = [
            {
                key: false,
                label: "Hidden"
            },
            {
                key: true,
                label: "Visible"
            }
        ];
        vm.statusOptionsPractice = [
            {
                key: false,
                label: "Closed"
            },
            {
                key: true,
                label: "Open for Practice"
            }
        ];
        vm.statusOptionsActive = [
            {
                key: true,
                label: "Active"
            }
        ];
        vm.showDropdown = showDropdown;
        vm.pendingChanges = pendingChanges;
        vm.validQuiz = validQuiz;
        vm.openCalendar = openCalendar;
        vm.addQuestion = addQuestion;
        vm.save = save;

        var unsubscribe = $rootScope.$on('artemisApp:quizExerciseUpdate', function (event, result) {
            vm.quizExercise = result;
        });
        $scope.$on('$destroy', unsubscribe);

        function openCalendar(date) {
            vm.datePickerOpenStatus[date] = true;
        }

        function showDropdown() {
            if (vm.quizExercise.isPlannedToStart) {
                var plannedEndMoment = moment(vm.quizExercise.releaseDate).add(vm.quizExercise.duration, "minutes");
                if (plannedEndMoment.isBefore(moment())) {
                    return "isOpenForPractice";
                } else if(moment(vm.quizExercise.releaseDate).isBefore(moment())) {
                    return "active";
                }
            }
            return "isVisibleBeforeStart";
        }

        function addQuestion() {
            // TODO
            alert("TODO");
        }

        function pendingChanges() {
            return [
                "title",
                "duration",
                "isPlannedToStart",
                "releaseDate",
                "status",
                "questions"
            ].some(function (key) {
                return vm.quizExercise[key] !== savedEntity[key];
            });
        }

        function validQuiz() {
            return vm.quizExercise.title && vm.quizExercise.title !== "" && vm.quizExercise.duration;
        }

        function save() {
            vm.isSaving = true;
            if (vm.quizExercise.id) {
                QuizExercise.update(vm.quizExercise, onSaveSuccess, onSaveError);
            } else {
                QuizExercise.save(vm.quizExercise, onSaveSuccess, onSaveError);
            }
        }
        function onSaveSuccess(result) {
            vm.isSaveing = false;
            savedEntity = Object.assign({}, result);
            vm.quizExercise = result;
        }
        function onSaveError() {
            alert("Saving Quiz Failed! Please try again later.");
            vm.isSaveing = false;
        }
    }
})();
