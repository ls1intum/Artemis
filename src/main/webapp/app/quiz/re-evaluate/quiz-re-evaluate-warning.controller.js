(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizReEvaluateWarningController',QuizReEvaluateWarningController);

    QuizReEvaluateWarningController.$inject = ['$uibModalInstance', 'entity', 'QuizExercise'/*, 'QuizExerciseReEvaluate'*/];

    function QuizReEvaluateWarningController($uibModalInstance, entity, QuizExercise/*, QuizExerciseReEvaluate*/) {
        var vm = this;

        vm.answerDeleted = false;
        vm.answerInvalid = false;
        vm.answerCorrectess = false;
        vm.questionDeleted = false;
        vm.questionInvalid = false;
        vm.scoringChanged = false;

        vm.quizExercise = entity;

        vm.clear = clear;
        vm.confirmChange = confirmChange;

        var backUpQuiz;

        QuizExercise.get({id: vm.quizExercise.id}).$promise.then(loadQuizSuccess);

        /**
         * check ,if the changes affect the existing results
         *  1. check if a question is deleted
         *  2. check for each question if:
         *          - it is set invalid
         *          - it has another scoringType
         *          - an answer was deleted
         *  3. check for each answer if:
         *          - it is set invalid
         *          - the correctness was changed
         *
         * @param quiz {quizExercise} the reference Quiz from Server
         */

        function loadQuizSuccess (quiz) {

            backUpQuiz = quiz;

            console.log(vm.quizExercise);
            console.log(backUpQuiz);

            // question deleted?
            vm.questionDeleted = (backUpQuiz.questions.length !== vm.quizExercise.questions.length);

            //check each question
            vm.quizExercise.questions.forEach( function (question) {
                //find same question in backUp (necessary if the order has been changed)
                var backUpQuestion = null;
                for (var i = 0; backUpQuestion === null && i < backUpQuiz.questions.length; i++) {
                    if(backUpQuiz.questions[i].id === question.id) {
                        backUpQuestion = backUpQuiz.questions[i];
                    }
                }
                if(backUpQuestion != null){
                    // question set invalid?
                    if(question.invalid !== backUpQuestion.invalid) {
                        vm.questionInvalid = true;
                    }
                    // answer deleted?
                    if(question.answerOptions.length !== backUpQuestion.answerOptions.length) {
                        vm.answerDeleted = true;
                    }
                    // question scoring changed?
                    if (question.scoringType !== backUpQuestion.scoringType) {
                        vm.scoringChanged = true;
                    }
                    //check each answer
                    question.answerOptions.forEach(function (answer) {
                        // only check if there are no changes on the answers yet
                        if (!vm.answerCorrectess || !vm.answerInvalid) {
                            var backUpAnswer = null;
                            for (var j = 0; backUpAnswer === null && j < backUpQuestion.answerOptions.length; j++) {
                                if (backUpQuestion.answerOptions[j].id === answer.id) {
                                    backUpAnswer = backUpQuestion.answerOptions[j];
                                }
                            }
                            if (backUpAnswer != null) {
                                //answer set invalid?
                                if (answer.invalid !== backUpAnswer.invalid) {
                                    vm.answerInvalid = true;
                                }
                                //answer correctness changed?
                                if (answer.isCorrect !== backUpAnswer.isCorrect) {
                                    vm.answerCorrectess = true;
                                }
                            }
                        }
                    });
                }

            });


        }

        /**
         * close modal
         */
        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        /**
         * Confirm changes
         *  => send changes to server and close modal
         */
        function confirmChange () {
            console.log(vm.quizExercise);
            alert("TO-DO:\n Open warning!\n Send Json to server\n close editor");
            $uibModalInstance.close(true);

            // QuizExerciseReEvaluate.update(vm.quizExercise,
            //     function () {
            //         $uibModalInstance.close(true);
            //     });
        }
    }
})();
