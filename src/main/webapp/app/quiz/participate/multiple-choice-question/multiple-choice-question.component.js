MultipleChoiceQuestionController.$inject = ['$translate', '$translatePartialLoader', '$scope', '$timeout', 'ArtemisMarkdown'];

function MultipleChoiceQuestionController($translate, $translatePartialLoader, $scope, $timeout, ArtemisMarkdown) {

    $translatePartialLoader.addPart('question');
    $translatePartialLoader.addPart('multipleChoiceQuestion');
    $translate.refresh();

    var vm = this;

    $scope.$watchCollection('vm.question', function () {
        // update html for text, hint and explanation for the question and every answer option
        vm.rendered = {
            text: ArtemisMarkdown.htmlForMarkdown(vm.question.text),
            hint: ArtemisMarkdown.htmlForMarkdown(vm.question.hint),
            explanation: ArtemisMarkdown.htmlForMarkdown(vm.question.explanation),
            answerOptions: vm.question.answerOptions.map(function(answerOption){
                return {
                    text: ArtemisMarkdown.htmlForMarkdown(answerOption.text),
                    hint: ArtemisMarkdown.htmlForMarkdown(answerOption.hint),
                    explanation: ArtemisMarkdown.htmlForMarkdown(answerOption.explanation)
                };
            })
        };
    });

    vm.toggleSelection = toggleSelection;

    function toggleSelection(answerOption) {
        if (vm.clickDisabled) {
            // Do nothing
            return;
        }
        if (isAnswerOptionSelected(answerOption)) {
            vm.selectedAnswerOptions = vm.selectedAnswerOptions.filter(function(selectedAnswerOption) {
                return selectedAnswerOption.id !== answerOption.id;
            });
        } else {
            vm.selectedAnswerOptions.push(answerOption);
        }
        // Note: I had to add a timeout of 0ms here, because the model changes are propagated asynchronously,
        // so we wait for one javascript event cycle before we inform the parent of changes
        $timeout(vm.onSelection, 0);
    }

    vm.isAnswerOptionSelected = isAnswerOptionSelected;

    function isAnswerOptionSelected(answerOption) {
        return vm.selectedAnswerOptions.findIndex(function(selected) {
            return selected.id === answerOption.id;
        }) !== -1;
    }
}

angular.module('artemisApp').component('multipleChoiceQuestion', {
    templateUrl: 'app/quiz/participate/multiple-choice-question/multiple-choice-question.html',
    controller: MultipleChoiceQuestionController,
    controllerAs: 'vm',
    bindings: {
        question: '<',
        selectedAnswerOptions: '=',
        clickDisabled: '<',
        showResult: '<',
        questionIndex: '<',
        score:'<',
        forceSampleSolution: '<',
        onSelection: '&'
    }
});
