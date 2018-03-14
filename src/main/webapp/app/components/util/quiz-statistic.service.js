(function () {
    'use strict';

    angular
        .module('artemisApp')
        .factory('QuizStatisticService', QuizStatisticService);

    QuizStatisticService.$inject = ['$state', 'QuizExercise'];

    function QuizStatisticService($state, QuizExercise) {

        var service = {
            previousStatistic: previousStatistic,
            nextStatistic: nextStatistic,
            releaseStatistics: releaseStatistics,
            releaseButtonDisabled: releaseButtonDisabled
        };

        return service;

        /**
         * got to the Template with the previous Statistic
         * if first QuestionStatistic -> go to the Quiz-Statistic
         *
         * @param quizExercise: the quizExercise with all statistics
         * @param question: the question of the current statistic
         */
        function previousStatistic(quizExercise, question) {
            //find position in quiz
            var index = quizExercise.questions.findIndex(function (quiz) {
                return quiz.id === question.id;
            });
            //go to quiz-Statistic if the position = 0
            if (index === 0) {
                $state.go('quiz-statistic-chart', {quizId: quizExercise.id});
            }
            //go to previous Question-statistic
            else {
                if (quizExercise.questions[index - 1].type === "multiple-choice") {
                    $state.go('multiple-choice-question-statistic-chart', {
                        quizId: quizExercise.id,
                        questionId: quizExercise.questions[index - 1].id
                    });
                }
                if (quizExercise.questions[index - 1].type === "drag-and-drop") {
                    $state.go('drag-and-drop-question-statistic-chart', {
                        quizId: quizExercise.id,
                        questionId: quizExercise.questions[index - 1].id
                    });
                }
            }
        }

        /**
         * got to the Template with the next Statistic
         * if last QuestionStatistic -> go to the Quiz-Point-Statistic
         *
         * @param quizExercise: the quizExercise with all statistics
         * @param question: the question of the current statistic
         */
        function nextStatistic(quizExercise, question) {
            //find position in quiz
            var index = quizExercise.questions.findIndex(function (quiz) {
                return quiz.id === question.id;
            });
            //go to quiz-Statistic if the position = last position
            if (index === quizExercise.questions.length - 1) {
                $state.go('quiz-point-statistic-chart', {quizId: quizExercise.id});
            }
            //go to next Question-statistic
            else {
                if (quizExercise.questions[index + 1].type === "multiple-choice") {
                    $state.go('multiple-choice-question-statistic-chart', {
                        quizId: quizExercise.id,
                        questionId: quizExercise.questions[index + 1].id
                    });
                }
                if (quizExercise.questions[index + 1].type === "drag-and-drop") {
                    $state.go('drag-and-drop-question-statistic-chart', {
                        quizId: quizExercise.id,
                        questionId: quizExercise.questions[index + 1].id
                    });
                }
            }
        }

        /**
         * release of revoke all statistics of the quizExercise
         *
         * @param quizExercise: the quiz, which statistics should be revoked or released
         * @param {boolean} released: true to release, false to revoke
         */
        function releaseStatistics(released, quizExercise) {
            if (released === quizExercise.quizPointStatistic.released) {
                return;
            }
            // check if it's allowed to release the statistics, if not send alert and do nothing
            if (released && releaseButtonDisabled(quizExercise)) {
                alert("Quiz hasn't ended yet!");
                return;
            }
            if (quizExercise.id) {
                quizExercise.quizPointStatistic.released = released;
                if (released) {
                    QuizExercise.releaseStatistics({id: quizExercise.id}, {},
                        function () {
                        },
                        function () {
                            alert("Error!");
                        });
                } else {
                    QuizExercise.revokeStatistics({id: quizExercise.id}, {});
                }
            }
        }

        /**
         * check if it's allowed to release the Statistic (allowed if the quiz is finished)
         *
         * @param quizExercise the quizExercise,
         *                      which will be checked if the release of the statistic is allowed
         * @returns {boolean} true if it's allowed, false if not
         */
        function releaseButtonDisabled(quizExercise) {
            if (quizExercise) {
                return (!quizExercise.isPlannedToStart
                    || moment().isBefore(quizExercise.dueDate));
            } else {
                return true;
            }
        }
    }
})();
