ReEvaluateMultipleChoiceQuestionController.$inject = ['$translate', '$translatePartialLoader', '$scope', '$timeout', 'ArtemisMarkdown'];

function ReEvaluateMultipleChoiceQuestionController($translate, $translatePartialLoader, $scope, $timeout, ArtemisMarkdown) {

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

    vm.sortableOptions = {
        handle: '.answer-handle',
        ignore: '.question-options',
        axis: "y",
        tolerance: 'pointer',
        cursor: 'move',
        start: function (e, ui) {
            ui.item.startPos = ui.item.index();
        },
        stop: function (e, ui) {
            var temp = vm.question.answerOptions.splice(ui.item.startPos, 1);
            vm.question.answerOptions.splice(ui.item.index(), 0, temp[0]);
            $scope.$apply();
        }
    };

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

            editor.setValue(ArtemisMarkdown.generateTextHintExplanation(vm.question));
            editor.clearSelection();

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
        var answerEditor;
        var i = 0;
        vm.question.answerOptions.forEach(function (answer) {
            requestAnimationFrame(function () {
                answerEditor = ace.edit("answer-content-editor-" + answer.id);
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

                generateAnswerMarkdown(answer);

                answerEditor.on("blur", function () {
                    var answerOptionEditor = ace.edit("answer-content-editor-" + answer.id);
                    parseAnswerMarkdown(answerOptionEditor.getValue(), answer);
                    vm.onUpdated();
                    $scope.$apply();
                });
            });
            i++;

        });

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
     */
    function generateAnswerMarkdown(answer) {
        var answerEditor = ace.edit("answer-content-editor-" + answer.id);
        var markdownText = (
            (answer.isCorrect ? "[x]" : "[ ]") + " " +
            ArtemisMarkdown.generateTextHintExplanation(answer)
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
     * 2. The question text is parsed with ArtemisMarkdown
     *
     * @param text {string} the markdown text to parse
     */
    function parseQuestionMarkdown(text) {
        // first split by [], [ ], [x] and [X]
        var questionParts = text.split(/\[\]|\[ \]|\[x\]|\[X\]/g);
        var questionText = questionParts[0];

        ArtemisMarkdown.parseTextHintExplanation(questionText, vm.question);
    }

    /**
     * Parse the an answer markdown and apply the result to the question's data
     *
     * The markdown rules are as follows:
     *
     * 1. Text starts with [x] or [ ] (also accepts [X] and [])
     *    => Answer options are marked as isCorrect depending on [ ] or [x]
     * 2. The answer text is parsed with ArtemisMarkdown
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

        ArtemisMarkdown.parseTextHintExplanation(answerOptionText, answer);
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
        vm.question.answerOptions.forEach(function (answer) {
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
        for (var i = 0; i < backUpQuestion.answerOptions.length; i++) {
            if (backUpQuestion.answerOptions[i].id === answer.id) {

                //find correct answer if they have another order
                vm.question.answerOptions[vm.question.answerOptions.indexOf(answer)] = angular.copy(backUpQuestion.answerOptions[i]);
                answer = angular.copy(backUpQuestion.answerOptions[i]);

                // reset answer editor
                setUpAnswerEditors();

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
        vm.question.answerOptions.splice(index, 1);

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
