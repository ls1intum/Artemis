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
    vm.resetQuestionTitle = resetQuestionTitle();
    vm.resetQuestion = resetQuestion();

    // set up editor
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
            //parseMarkdown(editor.getValue());
            vm.onUpdated();
            $scope.$apply();
        });
    });

    // set up answerEditor
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
            answerEditor[i].setHighlightActiveLine(false);
            answerEditor[i].setShowPrintMargin(false);

            generateAnswerMarkdown(answer);

            answerEditor[i].on("blur", function () {
                //parseMarkdown(answerEditor[i].getValue());
                vm.onUpdated();
                $scope.$apply();
            });
        });
        i++;
    });


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
     * @param {answerOption} answer is the AnswerOption, which the Markdown-field presents
     */
    function generateAnswerMarkdown(answer) {
        var markdownText = (
            (answer.isCorrect ? "[x]" : "[ ]") + " " +
            answer.text +
            (answer.hint ? "\n\t[-h] " + answer.hint : "") +
            (answer.explanation ? "\n\t[-e] " + answer.explanation : "")
        );
        answerEditor[i].setValue(markdownText);
        answerEditor[i].clearSelection();
    }

    /**
     * Parse the markdown and apply the result to the question's data
     *
     * The markdown rules are as follows:
     *
     * 1. Text is split at [x] and [ ] (also accepts [X] and [])
     *    => The first part (any text before the first [x] or [ ]) is the question text
     * 2. The question text is split at [-h] and [-e] tags.
     *    => First part is text. Everything after [-h] is Hint, anything after [-e] is explanation
     * 3. Every answer option (Parts after each [x] or [ ]) is also split at [-h] and [-e]
     *    => Same treatment as the question text for text, hint, and explanation
     *    => Answer options are marked as isCorrect depending on [ ] or [x]
     *
     * Note: Existing IDs for answer options are reused in the original order.
     *
     * @param text {string} the markdown text to parse
     */
    function parseMarkdown(text) {
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

        // extract existing answer option IDs
        var existingAnswerOptionIDs = vm.question.answerOptions
            .filter(function (answerOption) {
                return answerOption.id !== undefined && answerOption.id !== null;
            })
            .map(function(answerOption) {
                return answerOption.id;
            });
        vm.question.answerOptions = [];

        // work on answer options
        var endOfPreviusPart = text.indexOf(questionText) + questionText.length;
        for (var i = 1; i < questionParts.length; i++) {
            // find the box (text in-between the parts)
            var answerOption = {};
            var answerOptionText = questionParts[i];
            var startOfThisPart = text.indexOf(answerOptionText, endOfPreviusPart);
            var box = text.substring(endOfPreviusPart, startOfThisPart);
            // check if box says this answer option is correct or not
            answerOption.isCorrect = (box === "[x]" || box === "[X]");
            // update endOfPreviousPart for next loop
            endOfPreviusPart = startOfThisPart + answerOptionText.length;

            // parse this answerOption
            var answerOptionParts = answerOptionText.split(/\[\-e\]|\[\-h\]/g);
            answerOption.text = answerOptionParts[0].trim();
            if (answerOptionText.indexOf("[-h]") !== -1 && answerOptionText.indexOf("[-e]") !== -1) {
                if (answerOptionText.indexOf("[-h]") < answerOptionText.indexOf("[-e]")) {
                    answerOption.hint = answerOptionParts[1].trim();
                    answerOption.explanation = answerOptionParts[2].trim();
                } else {
                    answerOption.hint = answerOptionParts[2].trim();
                    answerOption.explanation = answerOptionParts[1].trim();
                }
            } else if (answerOptionText.indexOf("[-h]") !== -1) {
                answerOption.hint = answerOptionParts[1].trim();
                answerOption.expalanation = null;
            } else if (answerOptionText.indexOf("[-e]") !== -1) {
                answerOption.hint = null;
                answerOption.explanation = answerOptionParts[1].trim();
            } else {
                answerOption.hint = null;
                answerOption.explanation = null;
            }

            // assign existing ID if available
            if (vm.question.answerOptions.length < existingAnswerOptionIDs.length) {
                answerOption.id = existingAnswerOptionIDs[vm.question.answerOptions.length];
            }

            vm.question.answerOptions.push(answerOption);
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
     * Resets the the question title
     */
    function resetQuestionTitle() {
        //TO-DO
    }

    /**
     * Resets the the whole question
     */
    function resetQuestion() {
        //TO-DO
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
