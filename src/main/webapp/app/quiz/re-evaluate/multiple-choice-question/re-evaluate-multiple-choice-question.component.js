ReEvaluateMultipleChoiceQuestionController.$inject = ['$translate', '$translatePartialLoader', '$scope'];

function ReEvaluateMultipleChoiceQuestionController($translate, $translatePartialLoader, $scope) {

    $translatePartialLoader.addPart('question');
    $translatePartialLoader.addPart('multipleChoiceQuestion');
    $translate.refresh();

    var vm = this;
    vm.scoringTypeOptions = [
        {
            key: "ALL_OR_NOTHING",
            label: "All or Nothing"
        },
        {
            key: "PROPORTIONAL_CORRECT_OPTIONS",
            label: "Proportional Points for Correct Answer Options"
        },
        {
            key: "TRUE_FALSE_NEUTRAL",
            label: "True / False / No Answer"
        }
    ];
    //create BackUp for resets
    var backUpQuestion = angular.copy(vm.question);

    vm.resetQuestionTitle = resetQuestionTitle;
    vm.resetQuestionText = resetQuestionText;
    vm.resetQuestion = resetQuestion;
    vm.resetAnswer = resetAnswer;
    vm.deleteAnswer = deleteAnswer;
    vm.setAnswerInvalid = setAnswerInvalid;
    vm.isAnswerInvalid = isAnswerInvalid;

    setUpQuestionEditor();
    setUpAnswerEditors();
    /**
     * set up Question text editor
     */
    function setUpQuestionEditor() {
        vm.random = Math.random();
        var editor;
        requestAnimationFrame(function () {
            editor = ace.edit("question-content-editor-" + vm.random);
            editor.setTheme("ace/theme/chrome");
            editor.getSession().setMode("ace/mode/markdown");
            editor.renderer.setShowGutter(false);
            editor.renderer.setPadding(10);
            editor.renderer.setScrollMargin(8, 8);
            editor.setHighlightActiveLine(false);
            editor.setShowPrintMargin(false);

            generateQuestionMarkdown();

            editor.on("blur", function () {
                parseQuestionMarkdown(editor.getValue());
                vm.onUpdated();
                $scope.$apply();
            });
        });
    }

    /**
     * set up answerOption editors
     */
    function setUpAnswerEditors() {
    var answerEditor = new Array(vm.question.answerOptions.length);
    var i = 0;
    vm.question.answerOptions.forEach(function (answer) {
        requestAnimationFrame(function () {
            answerEditor[i] = ace.edit("answer-content-editor-" + answer.id);
            answerEditor[i].setTheme("ace/theme/chrome");
            answerEditor[i].getSession().setMode("ace/mode/markdown");
            answerEditor[i].renderer.setShowGutter(false);
            answerEditor[i].renderer.setPadding(10);
            answerEditor[i].renderer.setScrollMargin(8, 8);
            answerEditor[i].setOptions({
                autoScrollEditorIntoView: true
            });
            answerEditor[i].setHighlightActiveLine(false);
            answerEditor[i].setShowPrintMargin(false);

            generateAnswerMarkdown(answer, i);

            answerEditor[i].on("blur", function () {
                var answerOptionEditor = ace.edit("answer-content-editor-" + answer.id);
                parseAnswerMarkdown(answerOptionEditor.getValue(), answer);
                vm.onUpdated();
                $scope.$apply();
            });
            i++;
        });

    });

    }
    /**
     * generate the markdown text for this question
     *
     * The markdown is generated according to these rules:
     *
     * 1. First the question text is inserted
     * 2. If hint and/or explanation exist, they are added after the text with a linebreak and tab in front of them
     *
     */
    function generateQuestionMarkdown() {
        var editor = ace.edit("question-content-editor-" + vm.random);
        var markdownText = (
            vm.question.text +
            (vm.question.hint ? "\n\t[-h] " + vm.question.hint : "") +
            (vm.question.explanation ? "\n\t[-e] " + vm.question.explanation : "")
        );
        editor.setValue(markdownText);
        editor.clearSelection();
    }

    /**
     * generate the markdown text for this question
     *
     * The markdown is generated according to these rules:
     *
     * 1. First the answer text is inserted
     * 2. If hint and/or explanation exist, they are added after the text with a linebreak and tab in front of them
     *
     * @param answer {answerOption}  is the AnswerOption, which the Markdown-field presents
     * @param i {number} index of the answer in question.answerOptions
     */
    function generateAnswerMarkdown(answer, i) {
        var answerEditor = ace.edit("answer-content-editor-" + answer.id);
        var markdownText = (
            (answer.isCorrect ? "[x]" : "[ ]") + " " +
            answer.text +
            (answer.hint ? "\n\t[-h] " + answer.hint : "") +
            (answer.explanation ? "\n\t[-e] " + answer.explanation : "")
        );
        answerEditor.setValue(markdownText);
        answerEditor.clearSelection();
    }

    /**
     * Parse the question-markdown and apply the result to the question's data
     *
     * The markdown rules are as follows:
     *
     * 1. Text is split at [x] and [ ] (also accepts [X] and [])
     *    => The first part (any text before the first [x] or [ ]) is the question text
     * 2. The question text is split at [-h] and [-e] tags.
     *    => First part is text. Everything after [-h] is Hint, anything after [-e] is explanation
     *
     * @param text {string} the markdown text to parse
     */
    function parseQuestionMarkdown(text) {
        // first split by [], [ ], [x] and [X]
        var questionParts = text.split(/\[\]|\[ \]|\[x\]|\[X\]/g);
        var questionText = questionParts[0];

        // split question into main text, hint and explanation
        var questionTextParts = questionText.split(/\[\-e\]|\[\-h\]/g);
        vm.question.text = questionTextParts[0].trim();
        if (questionText.indexOf("[-h]") !== -1 && questionText.indexOf("[-e]") !== -1) {
            if (questionText.indexOf("[-h]") < questionText.indexOf("[-e]")) {
                vm.question.hint = questionTextParts[1].trim();
                vm.question.explanation = questionTextParts[2].trim();
            } else {
                vm.question.hint = questionTextParts[2].trim();
                vm.question.explanation = questionTextParts[1].trim();
            }
        } else if (questionText.indexOf("[-h]") !== -1) {
            vm.question.hint = questionTextParts[1].trim();
            vm.question.explanation = null;
        } else if (questionText.indexOf("[-e]") !== -1) {
            vm.question.hint = null;
            vm.question.explanation = questionTextParts[1].trim();
        } else {
            vm.question.hint = null;
            vm.question.explanation = null;
        }
    }

    /**
     * Parse the an answer markdown and apply the result to the question's data
     *
     * The markdown rules are as follows:
     *
     * 1. Text starts with [x] or [ ] (also accepts [X] and [])
     *    => Answer options are marked as isCorrect depending on [ ] or [x]
     * 2. The answer text is split at [-h] and [-e] tags.
     *    => First part is text. Everything after [-h] is Hint, anything after [-e] is explanation
     *
     * @param text {string} the markdown text to parse
     * @param answer {answerOption} the answer, where to save the result
     */
    function parseAnswerMarkdown(text, answer) {
        text = text.trim();
        // first split by [], [ ], [x] and [X]
        var answerParts = text.split(/\[\]|\[ \]|\[x\]|\[X\]/g);
        // work on answer options
            // find the box (text in-between the parts)
            var answerOption = {};
            var answerOptionText = answerParts[1];
            var startOfThisPart = text.indexOf(answerOptionText);
            var box = text.substring(0, startOfThisPart);
            // check if box says this answer option is correct or not
            answer.isCorrect = (box === "[x]" || box === "[X]");

            // parse this answerOption
            answerParts = answerOptionText.split(/\[\-e\]|\[\-h\]/g);
            answer.text = answerParts[0].trim();
            if (answerOptionText.indexOf("[-h]") !== -1 && answerOptionText.indexOf("[-e]") !== -1) {
                if (answerOptionText.indexOf("[-h]") < answerOptionText.indexOf("[-e]")) {
                    answer.hint = answerParts[1].trim();
                    answer.explanation = answerParts[2].trim();
                } else {
                    answer.hint = answerParts[2].trim();
                    answer.explanation = answerParts[1].trim();
                }
            } else if (answerOptionText.indexOf("[-h]") !== -1) {
                answer.hint = answerParts[1].trim();
                answer.expalanation = null;
            } else if (answerOptionText.indexOf("[-e]") !== -1) {
                answer.hint = null;
                answer.explanation = answerParts[1].trim();
            } else {
                answer.hint = null;
                answer.explanation = null;
            }
    }

    /**
     * watch for any changes to the question model and notify listener
     *
     * (use 'initializing' boolean to prevent $watch from firing immediately)
     */
    var initializing = true;
    $scope.$watchCollection('vm.question', function () {
        if (initializing) {
            initializing = false;
            return;
        }
        vm.onUpdated();
    });

    /**
     * delete this question from the quiz
     */
    vm.delete = function () {
        vm.onDelete();
    };

    /**
     * move this question one position up
     */
    vm.moveUp = function () {
        vm.onMoveUp();
    };

    /**
     * move this question one position down
     */
    vm.moveDown = function () {
        vm.onMoveDown();
    };

    /**
     * Resets the question title
     */
    function resetQuestionTitle() {
        vm.question.title = angular.copy(backUpQuestion.title);
    }
    /**
     * Resets the question text
     */
    function resetQuestionText() {
        vm.question.text = angular.copy(backUpQuestion.text);
        vm.question.expalanation = angular.copy(backUpQuestion.expalanation);
        vm.question.hint = angular.copy(backUpQuestion.hint);
        setUpQuestionEditor();
    }

    /**
     * Resets the whole question
     */
    function resetQuestion() {
        vm.question.title = angular.copy(backUpQuestion.title);
        vm.question.invalid = angular.copy(backUpQuestion.invalid);
        vm.question.randomizeOrder = angular.copy(backUpQuestion.randomizeOrder);
        vm.question.scoringType = angular.copy(backUpQuestion.scoringType);
        vm.question.answerOptions = angular.copy(backUpQuestion.answerOptions);
        vm.question.answerOptions.forEach( function (answer) {
           resetAnswer(answer);
        });
        resetQuestionText();
    }
    /**
     * Resets the whole answer
     *
     * @param answer {answerOption} the answer, which will be reset
     */
    function resetAnswer(answer) {
        for( var i = 0; i < backUpQuestion.answerOptions.length; i++) {
            if(backUpQuestion.answerOptions[i].id === answer.id) {

                vm.question.answerOptions[i] = angular.copy(backUpQuestion.answerOptions[i]);
                answer = vm.question.answerOptions[i];

                // reset answer editor
                requestAnimationFrame(function () {
                    var answerEditor = ace.edit("answer-content-editor-" + answer.id);
                    answerEditor.setTheme("ace/theme/chrome");
                    answerEditor.getSession().setMode("ace/mode/markdown");
                    answerEditor.renderer.setShowGutter(false);
                    answerEditor.renderer.setPadding(10);
                    answerEditor.renderer.setScrollMargin(8, 8);
                    answerEditor.setOptions({
                        autoScrollEditorIntoView: true
                    });
                    answerEditor.setHighlightActiveLine(false);
                    answerEditor.setShowPrintMargin(false);

                    generateAnswerMarkdown(answer, vm.question.answerOptions.indexOf(answer));

                    answerEditor.on("blur", function () {
                        var answerOptionEditor = ace.edit("answer-content-editor-" + answer.id);
                        parseAnswerMarkdown(answerOptionEditor.getValue(), answer);
                        vm.onUpdated();
                        $scope.$apply();
                    });

                });

            }
        }
    }
    /**
     * Delete the answer
     *
     * @param  answer {answerOption} the Answer which should be deleted
     */
    function deleteAnswer(answer) {
        var index = vm.question.answerOptions.indexOf(answer);
        vm.question.answerOptions.splice(index,1);
        vm.onUpdated();
    }

    /**
     * Set the answer invalid
     *
     * @param  answer {answerOption} the Answer which should be deleted
     */
    function setAnswerInvalid(answer) {

        vm.question.answerOptions[vm.question.answerOptions.indexOf(answer)].invalid = true;
        vm.onUpdated();
    }

    /**
     * Checks if the given answer is invalid
     *
     * @param  answer {answerOption} the Answer which should be checked
     * @return {boolean} true if the answer is invalid
     */
    function isAnswerInvalid(answer) {

        return answer.invalid;
    }
}

angular.module('artemisApp').component('reEvaluateMultipleChoiceQuestion', {
    templateUrl: 'app/quiz/re-evaluate/multiple-choice-question/re-evaluate-multiple-choice-question.html',
    controller: ReEvaluateMultipleChoiceQuestionController,
    controllerAs: 'vm',
    bindings: {
        question: '=',
        onDelete: '&',
        onUpdated: '&',
        questionIndex: '<',
        onMoveUp: '&',
        onMoveDown: '&'
    }
});
