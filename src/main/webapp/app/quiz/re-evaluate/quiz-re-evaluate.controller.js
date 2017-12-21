(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizReEvaluateController', QuizReEvaluateController);

    QuizReEvaluateController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'QuizExercise', 'Question', 'QuizPointStatistic', 'courseEntity'];

    function QuizReEvaluateController($scope, $rootScope, $stateParams, previousState, entity, QuizExercise, Question, QuizPointStatistic, courseEntity) {
        var vm = this;

        prepareEntity(entity);

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
        //create BackUp for resets
        var backUpQuiz = angular.copy(vm.quizExercise);

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
        vm.pendingChanges = pendingChanges;
        vm.validQuiz = validQuiz;
        vm.deleteQuestion = deleteQuestion;
        vm.onQuestionUpdated = onQuestionUpdated;
        vm.save = save;
        vm.onDurationChange = onDurationChange;
        vm.durationString = durationString;
        vm.resetAll = resetAll;
        vm.resetQuizTitle = resetQuizTitle;
        vm.moveUp = moveUp;
        vm.moveDown = moveDown;

        var unsubscribe = $rootScope.$on('artemisApp:quizExerciseUpdate', function (event, result) {
            vm.quizExercise = result;
        });
        $scope.$on('$destroy', unsubscribe);

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
            return !angular.equals(vm.quizExercise, backUpQuiz);
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

            console.log(vm.quizExercise);
            alert("TO-DO:\n Open warning!\n Send Json to server\n close editor");
            return;

            vm.isSaving = true;
            if (vm.quizExercise.id) {
                QuizExercise.update(vm.quizExercise, onSaveSuccess, onSaveError);
            } else {
                QuizExercise.save(vm.quizExercise, onSaveSuccess, onSaveError);
            }
        }
        /**
         * Updates the backUpQuiz and the vm.quizExercise with the result
         *
         * @param result {QuizExercise} the saved quizExercise-Object
         */
        function onSaveSuccess(result) {
            vm.isSaving = false;
            prepareEntity(result);
            backUpQuiz = angular.copy(vm.quizExercise);
            vm.quizExercise = result;
        }

        /**
         * Send alert if the saving failed
         */
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

        /**
         * Gives the duration time in a String with this format: <minutes>:<seconds>
         *@returns {String} the duration as String
         */
        function durationString() {
            if(vm.duration.seconds <= 0){
                return vm.duration.minutes + ":00";
            }
            if(vm.duration.seconds < 10){
                return vm.duration.minutes + ":0" + vm.duration.seconds;
            }
            return vm.duration.minutes + ":" + vm.duration.seconds;
        }

        /**
         * Resets the whole Quiz
         */
        function resetAll() {
            vm.quizExercise = angular.copy(backUpQuiz);
        }

        /**
         * Resets the quiz title
         */
        function resetQuizTitle() {
            vm.quizExercise.title = angular.copy(backUpQuiz.title);
        }

        /**
         * move the question one position up
         * @param question {Question} the question to move
         */
        function moveUp(question) {
            var index = vm.quizExercise.questions.indexOf(question);
            if(index === 0) {
                return;
            }
            var tempQuestions = angular.copy(vm.quizExercise.questions);
            var temp = tempQuestions[index];
            tempQuestions[index] = tempQuestions[index-1];
            tempQuestions[index-1] = temp;
            vm.quizExercise.questions = tempQuestions;
        }

        /**
         * move the question one position down
         * @param question {Question} the question to move
         */
        function moveDown(question) {
            var index = vm.quizExercise.questions.indexOf(question);
            if(index === (vm.quizExercise.questions.length - 1)) {
                return;
            }
            var tempQuestions = angular.copy(vm.quizExercise.questions);
            var temp = tempQuestions[index];
            tempQuestions[index] = tempQuestions[index+1];
            tempQuestions[index+1] = temp;
            vm.quizExercise.questions = tempQuestions;
        }
    }
})();
