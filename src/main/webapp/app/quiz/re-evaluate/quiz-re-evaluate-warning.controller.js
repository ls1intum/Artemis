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
                var backUpQuestion = null;
                for (var i = 0; backUpQuestion === null && i < backUpQuiz.questions.length; i++) {
                    if (backUpQuiz.questions[i].id === question.id) {
                        backUpQuestion = backUpQuiz.questions[i];
                    }
                }
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
                        // question-Element deleted?
                        if (question.answerOptions.length !== backUpQuestion.answerOptions.length) {
                            vm.questionElementDeleted = true;
                        }

                        //check each answer
                        question.answerOptions.forEach(function (answer) {
                            // only check if there are no changes on the question-elements yet
                            if (!vm.questionCorrectness || !vm.questionElementInvalid) {
                                var backUpAnswer = null;
                                for (var j = 0; backUpAnswer === null && j < backUpQuestion.answerOptions.length; j++) {
                                    if (backUpQuestion.answerOptions[j].id === answer.id) {
                                        backUpAnswer = backUpQuestion.answerOptions[j];
                                    }
                                }
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
                    //check DragAndDropQuestions
                    if (question.type === "drag-and-drop") {
                        if (question.dragItems.length !== backUpQuestion.dragItems.length
                            || question.dropLocations.length !== backUpQuestion.dropLocations.length) {
                            vm.questionElementDeleted = true;
                        }
                        if (!angular.equals(question.correctMappings, backUpQuestion.correctMappings)) {
                            vm.questionCorrectess = true;
                        }

                        //check each dragItem
                        question.dragItems.forEach(function (dragItem) {
                        // only check if there are no changes on the question-elements yet
                            if (!vm.questionElementInvalid) {
                                var backUpDragItem = null;
                                for (var j = 0; backUpDragItem === null && j < backUpQuestion.dragItems.length; j++) {
                                    if (backUpQuestion.dragItems[j].id === dragItem.id) {
                                        backUpDragItem = backUpQuestion.dragItems[j];
                                    }
                                }
                                if (backUpDragItem !== null) {
                                    //dragItem set invalid?
                                    if (dragItem.invalid !== backUpDragItem.invalid) {
                                        vm.questionElementInvalid = true;
                                    }
                                }
                            }
                        });

                        //check each dropLocation
                        question.dropLocations.forEach(function (dropLocation) {
                            // only check if there are no changes on the question-elements yet
                            if (!vm.questionElementInvalid) {
                                var backUpDropLocation = null;
                                for (var j = 0; backUpDropLocation === null && j < backUpQuestion.dropLocations.length; j++) {
                                    if (backUpQuestion.dropLocations[j].id === dropLocation.id) {
                                        backUpDropLocation = backUpQuestion.dropLocations[j];
                                    }
                                }
                                if (backUpDropLocation !== null) {
                                    //dropLocation set invalid?
                                    if (dropLocation.invalid !== backUpDropLocation.invalid) {
                                        vm.questionElementInvalid = true;
                                    }
                                }
                            }
                        });
                    }



                }
            });
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
