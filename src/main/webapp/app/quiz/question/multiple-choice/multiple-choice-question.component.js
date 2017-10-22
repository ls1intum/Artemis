MultipleChoiceQuestionController.$inject = ['$translate', '$translatePartialLoader', '$scope'];

function MultipleChoiceQuestionController($translate, $translatePartialLoader, $scope) {

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
    vm.addCorrectAnswerOption = addCorrectAnswerOption;
    vm.addIncorrectAnswerOption = addIncorrectAnswerOption;
    vm.addHintAtCursor = addHintAtCursor;
    vm.addExplanationAtCursor = addExplanationAtCursor;

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

        generateMarkdown();

        editor.on("blur", function () {
            parseMarkdown(editor.getValue());
            vm.onUpdated();
            $scope.$apply();
        });
    });

    /**
     * generate the markdown text for this question
     */
    function generateMarkdown() {
        var markdownText = (
            vm.question.text +
            (vm.question.hint ? "\n\t[-h] " + vm.question.hint : "") +
            (vm.question.explanation ? "\n\t[-e] " + vm.question.explanation : "") +
            "\n\n" +
            vm.question.answerOptions.map(function (answerOption) {
                return (
                    (answerOption.isCorrect ? "[x]" : "[ ]") + " " +
                    answerOption.text +
                    (answerOption.hint ? "\n\t[-h] " + answerOption.hint : "") +
                    (answerOption.explanation ? "\n\t[-e] " + answerOption.explanation : "")
                );
            }).join("\n")
        );
        editor.setValue(markdownText);
        editor.clearSelection();
    }

    /**
     * Parse the markdown and apply the result to the question's data
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

        vm.question.answerOptions = [];
        // work on answer options
        var endOfPreviusPart = text.indexOf(questionText) + questionText.length;;
        for(var i = 1; i < questionParts.length; i++) {
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

            vm.question.answerOptions.push(answerOption);
        }
    }

    /**
     * add the markdown for a correct answerOption at the end of the current markdown text
     */
    function addCorrectAnswerOption() {
        var currentText = editor.getValue();
        var addedText = "\n[x] Enter a correct answer option here";
        currentText += addedText;
        editor.setValue(currentText);
        editor.focus();
        var lines = currentText.split("\n").length;
        var range = editor.selection.getRange();
        range.setStart(lines-1, 4);
        range.setEnd(lines-1, addedText.length - 1);
        console.log(range);
        editor.selection.setRange(range);
    }

    /**
     * add the markdown for an incorrect answerOption at the end of the current markdown text
     */
    function addIncorrectAnswerOption() {
        var currentText = editor.getValue();
        var addedText = "\n[ ] Enter an incorrect answer option here";
        currentText += addedText;
        editor.setValue(currentText);
        editor.focus();
        var lines = currentText.split("\n").length;
        var range = editor.selection.getRange();
        range.setStart(lines-1, 4);
        range.setEnd(lines-1, addedText.length - 1);
        editor.selection.setRange(range);
    }

    /**
     * add the markdown for a hint at the current cursor location
     */
    function addHintAtCursor() {
        var addedText = "\n\t[-h] Add a hint here (visible during the quiz via \"?\"-Button)";
        editor.focus();
        editor.insert(addedText);
        var range = editor.selection.getRange();
        range.setStart(range.start.row, range.start.column - addedText.length + 7);
        editor.selection.setRange(range);
    }

    /**
     * add the markdown for an explanation at the current cursor location
     */
    function addExplanationAtCursor() {
        var addedText = "\n\t[-e] Add an explanation here (only visible in feedback after quiz has ended)";
        editor.focus();
        editor.insert(addedText);
        var range = editor.selection.getRange();
        range.setStart(range.start.row, range.start.column - addedText.length + 7);
        editor.selection.setRange(range);
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
}

angular.module('artemisApp').component('multipleChoiceQuestion', {
    templateUrl: 'app/quiz/question/multiple-choice/multiple-choice-question.html',
    controller: MultipleChoiceQuestionController,
    controllerAs: 'vm',
    bindings: {
        question: '=',
        onDelete: '&',
        onUpdated: '&'
    }
});
