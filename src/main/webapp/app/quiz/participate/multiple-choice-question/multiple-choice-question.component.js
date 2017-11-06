MultipleChoiceQuestionController.$inject = ['$translate', '$translatePartialLoader', '$scope', '$sanitize'];

function MultipleChoiceQuestionController($translate, $translatePartialLoader, $scope, $sanitize) {

    $translatePartialLoader.addPart('question');
    $translatePartialLoader.addPart('multipleChoiceQuestion');
    $translate.refresh();

    var vm = this;

    vm.rendered = {
        text: htmlForMarkdown(vm.question.text),
        hint: htmlForMarkdown(vm.question.hint),
        answerOptions: vm.question.answerOptions.map(function(answerOption){
            return {
                text: htmlForMarkdown(answerOption.text),
                hint: htmlForMarkdown(answerOption.hint)
            };
        })
    };

    vm.toggleSelection = toggleSelection;

    function toggleSelection(answerOption) {
        if (isAnswerOptionSelected(answerOption)) {
            vm.selectedAnswerOptions = vm.selectedAnswerOptions.filter(function(ao) {
                return ao.id !== answerOption.id;
            });
        } else {
            vm.selectedAnswerOptions.push(answerOption);
        }
    }

    vm.isAnswerOptionSelected = isAnswerOptionSelected;

    function isAnswerOptionSelected(answerOption) {
        return vm.selectedAnswerOptions.findIndex(function(selected) {
            return selected.id === answerOption.id;
        }) !== -1;
    }

    /**
     * converts markdown into html
     * @param {string} markdownText the original markdown text
     * @returns {string} the resulting html as a string
     */
    function htmlForMarkdown(markdownText) {
        var converter = new showdown.Converter({
            parseImgDimensions: true,
            headerLevelStart: 3,
            simplifiedAutoLink: true,
            excludeTrailingPunctuationFromURLs: true,
            strikethrough: true,
            tables: true,
            openLinksInNewWindow: true,
            backslashEscapesHTMLTags: true
        });
        var html = converter.makeHtml(markdownText);
        return $sanitize(html);
    }

}

angular.module('artemisApp').component('multipleChoiceQuestion', {
    templateUrl: 'app/quiz/participate/multiple-choice-question/multiple-choice-question.html',
    controller: MultipleChoiceQuestionController,
    controllerAs: 'vm',
    bindings: {
        question: '=',
        selectedAnswerOptions: '='
    }
});
