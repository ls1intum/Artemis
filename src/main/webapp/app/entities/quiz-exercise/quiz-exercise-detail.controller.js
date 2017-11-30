(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizExerciseDetailController', QuizExerciseDetailController);

    QuizExerciseDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'QuizExercise', 'Question', 'courseEntity'];

    function QuizExerciseDetailController($scope, $rootScope, $stateParams, previousState, entity, QuizExercise, Question, courseEntity) {
        var vm = this;

        prepareEntity(entity);

        var savedEntity = entity.id ? Object.assign({}, entity) : {};

        vm.quizExercise = entity;
        vm.previousState = previousState.name;
        vm.quizExercise.course = courseEntity;
        vm.datePickerOpenStatus = {
            releaseDate: false
        };
        vm.isSaving = false;
        vm.true = true;
        vm.duration = {
            minutes: 0,
            seconds: 0
        };

        // status options depending on relationship between start time, end time, and current time
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

        // make functions available to html
        vm.showDropdown = showDropdown;
        vm.pendingChanges = pendingChanges;
        vm.validQuiz = validQuiz;
        vm.openCalendar = openCalendar;
        vm.addQuestion = addQuestion;
        vm.deleteQuestion = deleteQuestion;
        vm.onQuestionUpdated = onQuestionUpdated;
        vm.save = save;
        vm.onDurationChange = onDurationChange;

        var unsubscribe = $rootScope.$on('artemisApp:quizExerciseUpdate', function (event, result) {
            vm.quizExercise = result;
        });
        $scope.$on('$destroy', unsubscribe);

        function openCalendar(date) {
            vm.datePickerOpenStatus[date] = true;
        }

        /**
         * Determine which dropdown to display depending on the relationship between start time, end time, and current time
         * @returns {string} the name of the dropdown to show
         */
        function showDropdown() {
            if (vm.quizExercise.isPlannedToStart) {
                var plannedEndMoment = moment(vm.quizExercise.releaseDate).add(vm.quizExercise.duration, "seconds");
                if (plannedEndMoment.isBefore(moment())) {
                    return "isOpenForPractice";
                } else if (moment(vm.quizExercise.releaseDate).isBefore(moment())) {
                    return "active";
                }
            }
            return "isVisibleBeforeStart";
        }

        /**
         * Add an empty question to the quiz
         */
        function addQuestion() {
            vm.quizExercise.questions = vm.quizExercise.questions.concat([{
                title: "",
                text: "Enter your question text here",
                scoringType: "ALL_OR_NOTHING",
                randomizeOrder: false,
                score: 1,
                type: "multiple-choice",
                answerOptions: [
                    {
                        isCorrect: true,
                        text: "Enter a correct answer option here"
                    },
                    {
                        isCorrect: false,
                        text: "Enter an incorrect answer option here"
                    }
                ]
            }]);
        }

        /**
         * Remove question from the quiz
         * @param question {Question} the question to remove
         */
        function deleteQuestion(question) {
            vm.quizExercise.questions = vm.quizExercise.questions.filter(function (q) {
                return q !== question;
            });
        }

        /**
         * Handles the change of a question by replacing the array with a copy (allows for shallow comparison)
         */
        function onQuestionUpdated() {
            vm.quizExercise.questions = Array.from(vm.quizExercise.questions);
        }

        /**
         * Determine if there are any changes waiting to be saved
         * @returns {boolean} true if there are any pending changes, false otherwise
         */
        function pendingChanges() {
            return [
                "title",
                "duration",
                "isPlannedToStart",
                "releaseDate",
                "isVisibleBeforeStart",
                "isOpenForPractice",
                "questions"
            ].some(function (key) {
                return vm.quizExercise[key] !== savedEntity[key];
            });
        }

        /**
         * Check if the current inputs are valid
         * @returns {boolean} true if valid, false otherwise
         */
        function validQuiz() {
            return vm.quizExercise.title && vm.quizExercise.title !== "" && vm.quizExercise.duration;
        }

        /**
         * Save the quiz to the server
         */
        function save() {
            vm.isSaving = true;
            if (vm.quizExercise.id) {
                QuizExercise.update(vm.quizExercise, onSaveSuccess, onSaveError);
            } else {
                QuizExercise.save(vm.quizExercise, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess(result) {
            vm.isSaving = false;
            prepareEntity(result);
            savedEntity = Object.assign({}, result);
            vm.quizExercise = result;
        }

        function onSaveError() {
            alert("Saving Quiz Failed! Please try again later.");
            vm.isSaving = false;
        }

        /**
         * Makes sure the entity is well formed and its fields are of the correct types
         * @param entity
         */
        function prepareEntity(entity) {
            entity.releaseDate = entity.releaseDate ? new Date(entity.releaseDate) : new Date();
            entity.duration = Number(entity.duration);
            entity.duration = isNaN(entity.duration) ? 10 : entity.duration;
        }

        // keep ui up to date when duration changes
        $scope.$watch("vm.quizExercise.duration", function () {
            updateDuration();
        });

        /**
         * Reach to changes of duration inputs by updating model and ui
         */
        function onDurationChange() {
            var duration = moment.duration(vm.duration);
            vm.quizExercise.duration = Math.min(Math.max(duration.asSeconds(), 0), 10 * 60 * 60);
            updateDuration();
        }

        /**
         * update ui to current value of duration
         */
        function updateDuration() {
            var duration = moment.duration(vm.quizExercise.duration, "seconds");
            vm.duration.minutes = 60 * duration.hours() + duration.minutes();
            vm.duration.seconds = duration.seconds();
        }
    }
})();
