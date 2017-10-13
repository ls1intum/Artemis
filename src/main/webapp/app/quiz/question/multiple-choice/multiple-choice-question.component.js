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

        editor.renderer.$cursorLayer.element.style.display = "none";
        editor.on("focus", function () {
            editor.renderer.$cursorLayer.element.style.display = "";
        });
        editor.on("blur", function () {
            editor.renderer.$cursorLayer.element.style.display = "none";
        });
    });

    function generateMarkdown() {
        if (!vm.question.answerOptions || vm.question.answerOptions.length === 0) {
            vm.question.answerOptions = [
                {
                    isCorrect: true,
                    text: "Enter a correct answer option here",
                    hint: "Enter a hint here",
                    explanation: "Enter an explanation here"
                },
                {
                    isCorrect: false,
                    text: "Enter an incorrect answer option here",
                    hint: "Enter a hint here",
                    explanation: "Enter an explanation here"
                }
            ];
        }
        var markdownText = (
            (vm.question.text || "Enter your question text here") + "\n" +
            "[-h] " + (vm.question.hint || "Enter a hint here (can be accessed during quiz by clicking \"?\"-Button)") + "\n" +
            "[-e] " + (vm.question.explanation || "Enter an explanation here (only visible in feedback)") + "\n\n" +
            vm.question.answerOptions.map(function (answerOption) {
                return (
                    (answerOption.isCorrect ? "[x]" : "[ ]") + " " +
                    answerOption.text +
                    (answerOption.hint ? "\t[-h] " + (answerOption.hint) : "") +
                    (answerOption.explanation ? "\t[-e] " + (answerOption.explanation) : "")

                );
            }).join("\n")
        );
        editor.setValue(markdownText);
        editor.clearSelection();
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
