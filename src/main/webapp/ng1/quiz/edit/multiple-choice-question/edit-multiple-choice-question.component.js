EditMultipleChoiceQuestionController.$inject = ['$translate', '$translatePartialLoader', '$scope', 'ArtemisMarkdown'];

function EditMultipleChoiceQuestionController($translate, $translatePartialLoader, $scope, ArtemisMarkdown) {

    $translatePartialLoader.addPart('quizExercise');
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
            key: "PROPORTIONAL_WITH_PENALTY",
            label: "Proportional with Penalty"
        }
    ];
    vm.addCorrectAnswerOption = addCorrectAnswerOption;
    vm.addIncorrectAnswerOption = addIncorrectAnswerOption;
    vm.addHintAtCursor = addHintAtCursor;
    vm.addExplanationAtCursor = addExplanationAtCursor;
    vm.showPreview = false;
    vm.togglePreview = togglePreview;

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
     *
     * The markdown is generated according to these rules:
     *
     * 1. First the question text, hint, and explanation are added using ArtemisMarkdown
     * 2. After an empty line, the answer options are added
     * 3. For each answer option: text, hint and explanation are added using ArtemisMarkdown
     *
     */
    function generateMarkdown() {
        var markdownText = (
            ArtemisMarkdown.generateTextHintExplanation(vm.question) +
            "\n\n" +
            vm.question.answerOptions.map(function (answerOption) {
                return (
                    (answerOption.isCorrect ? "[x]" : "[ ]") + " " +
                    ArtemisMarkdown.generateTextHintExplanation(answerOption)
                );
            }).join("\n")
        );
        editor.setValue(markdownText);
        editor.clearSelection();
    }

    /**
     * Parse the markdown and apply the result to the question's data
     *
     * The markdown rules are as follows:
     *
     * 1. Text is split at [x] and [ ] (also accepts [X] and [])
     *    => The first part (any text before the first [x] or [ ]) is the question text
     * 2. The question text is split into text, hint, and explanation using ArtemisMarkdown
     * 3. For every answer option (Parts after each [x] or [ ]):
     *    3.a) Same treatment as the question text for text, hint, and explanation
     *    3.b) Answer options are marked as isCorrect depending on [ ] or [x]
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
        ArtemisMarkdown.parseTextHintExplanation(questionText, vm.question);

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
            ArtemisMarkdown.parseTextHintExplanation(answerOptionText, answerOption);

            // assign existing ID if available
            if (vm.question.answerOptions.length < existingAnswerOptionIDs.length) {
                answerOption.id = existingAnswerOptionIDs[vm.question.answerOptions.length];
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
        range.setStart(lines - 1, 4);
        range.setEnd(lines - 1, addedText.length - 1);
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
        range.setStart(lines - 1, 4);
        range.setEnd(lines - 1, addedText.length - 1);
        editor.selection.setRange(range);
    }

    /**
     * add the markdown for a hint at the current cursor location
     */
    function addHintAtCursor() {
        ArtemisMarkdown.addHintAtCursor(editor);
    }

    /**
     * add the markdown for an explanation at the current cursor location
     */
    function addExplanationAtCursor() {
        ArtemisMarkdown.addExplanationAtCursor(editor);
    }

    function togglePreview() {
        vm.showPreview = !vm.showPreview;
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

angular.module('artemisApp').component('editMultipleChoiceQuestion', {
    templateUrl: 'ng1/quiz/edit/multiple-choice-question/edit-multiple-choice-question.html',
    controller: EditMultipleChoiceQuestionController,
    controllerAs: 'vm',
    bindings: {
        question: '=',
        onDelete: '&',
        onUpdated: '&',
        questionIndex: '<'
    }
});
