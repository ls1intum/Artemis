(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizReEvaluateWarningController', QuizReEvaluateWarningController);

    QuizReEvaluateWarningController.$inject = ['$uibModalInstance', 'entity', 'QuizExercise', 'QuizExerciseReEvaluate'];

    function QuizReEvaluateWarningController($uibModalInstance, entity, QuizExercise, QuizExerciseReEvaluate) {
        var vm = this;

        vm.successful = false;
        vm.failed = false;
        vm.busy = false;

        vm.questionElementDeleted = false;
        vm.questionElementInvalid = false;
        vm.questionCorrectness = false;
        vm.questionDeleted = false;
        vm.questionInvalid = false;
        vm.scoringChanged = false;

        vm.quizExercise = entity;

        vm.clear = clear;
        vm.confirmChange = confirmChange;
        vm.close =  close;

        var backUpQuiz;

        QuizExercise.get({id: vm.quizExercise.id}).$promise.then(loadQuizSuccess);

        /**
         * check ,if the changes affect the existing results
         *  1. check if a question is deleted
         *  2. check for each question if:
         *          - it is set invalid
         *          - it has another scoringType
         *          - an answer was deleted
         *  3. check for each question-element if:
         *          - it is set invalid
         *          - the correctness was changed
         *
         * @param quiz {quizExercise} the reference Quiz from Server
         */
        function loadQuizSuccess(quiz) {

            backUpQuiz = quiz;

            // question deleted?
            vm.questionDeleted = (backUpQuiz.questions.length !== vm.quizExercise.questions.length);

            //check each question
            vm.quizExercise.questions.forEach(function (question) {
                //find same question in backUp (necessary if the order has been changed)
                var backUpQuestion = backUpQuiz.questions.find(function (questionBackUp) {
                    return question.id === questionBackUp.id
                });

                checkQuestion(question, backUpQuestion);

            });
        }

        /**
         * 1. compare backUpQuestion and question
         * 2. set flags based on detected changes
         *
         * @param question changed question
         * @param backUpQuestion original not changed question
         */
        function checkQuestion(question, backUpQuestion) {

            if (backUpQuestion !== null) {
                // question set invalid?
                if (question.invalid !== backUpQuestion.invalid) {
                    vm.questionInvalid = true;
                }
                // question scoring changed?
                if (question.scoringType !== backUpQuestion.scoringType) {
                    vm.scoringChanged = true;
                }
                //check MultipleChoiceQuestions
                if (question.type === "multiple-choice") {
                    checkMultipleChoiceQuestion(question, backUpQuestion);
                }
                //check DragAndDropQuestions
                if (question.type === "drag-and-drop") {
                    checkDragAndDropQuestion(question, backUpQuestion);

                }
            }
        }

        /**
         * 1. check MultipleChoiceQuestion-Elements
         * 2. set flags based on detected changes
         *
         * @param question changed Multiple-Choice-Question
         * @param backUpQuestion original not changed Multiple-Choice-Question
         */
        function checkMultipleChoiceQuestion(question, backUpQuestion) {
            // question-Element deleted?
            if (question.answerOptions.length !== backUpQuestion.answerOptions.length) {
                vm.questionElementDeleted = true;
            }
            //check each answer
            question.answerOptions.forEach(function (answer) {
                // only check if there are no changes on the question-elements yet
                if (!vm.questionCorrectness || !vm.questionElementInvalid) {
                    var backUpAnswer = backUpQuestion.answerOptions.find(function (answerBackUp) {
                        return answerBackUp.id === answer.id;
                    });
                    if (backUpAnswer !== null) {
                        //answer set invalid?
                        if (answer.invalid !== backUpAnswer.invalid) {
                            vm.questionElementInvalid = true;
                        }
                        //answer correctness changed?
                        if (answer.isCorrect !== backUpAnswer.isCorrect) {
                            vm.questionCorrectness = true;
                        }
                    }
                }
            });
        }

        /**
         * 1. check DragAndDrop-Question-Elements
         * 2. set flags based on detected changes
         *
         * @param question changed DragAndDrop-Question
         * @param backUpQuestion original not changed DragAndDrop-Question
         */
        function checkDragAndDropQuestion(question, backUpQuestion) {
            //check if a dropLocation or dragItem was deleted
            if (question.dragItems.length !== backUpQuestion.dragItems.length
                || question.dropLocations.length !== backUpQuestion.dropLocations.length) {
                vm.questionElementDeleted = true;
            }
            //check if the correct Mappings has changed
            if (!angular.equals(question.correctMappings, backUpQuestion.correctMappings)) {
                vm.questionCorrectness = true;
            }
            // only check if there are no changes on the question-elements yet
            if (!vm.questionElementInvalid) {
                //check each dragItem
                question.dragItems.forEach(function (dragItem) {
                    var backUpDragItem = backUpQuestion.dragItems.find(function (dragItemBackUp) {
                        return dragItemBackUp.id === dragItem.id;
                    });
                    //dragItem set invalid?
                    if (backUpDragItem !== null
                        && dragItem.invalid !== backUpDragItem.invalid) {
                        vm.questionElementInvalid = true;
                    }
                });
                //check each dropLocation
                question.dropLocations.forEach(function (dropLocation) {
                    var backUpDropLocation = backUpQuestion.dropLocations.find(function (dropLocationBackUp) {
                        return dropLocationBackUp.id === dropLocation.id;
                    });
                    //dropLocation set invalid?
                    if (backUpDropLocation !== null
                        && dropLocation.invalid !== backUpDropLocation.invalid) {
                        vm.questionElementInvalid = true;
                    }
                });
            }
        }

        /**
         * close modal
         */
        function clear() {
            $uibModalInstance.dismiss('cancel');
        }

        /**
         * Confirm changes
         *  => send changes to server and wait for result
         *  if saving failed -> show failed massage
         */
        function confirmChange() {

            vm.busy = true;

            QuizExerciseReEvaluate.update(vm.quizExercise,
                function () {
                    vm.busy = false;
                    vm.successful = true;
                },
                function () {
                    vm.busy = false;
                    vm.failed = true;
                });
        }

        /**
         * close modal and go back to QuizExercise-Overview
         */
        function close() {
            $uibModalInstance.close(true);
        }
    }
})();
