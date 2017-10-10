MultipleChoiceQuestionController.$inject = ['$translate', '$translatePartialLoader'];

function MultipleChoiceQuestionController($translate, $translatePartialLoader) {

    $translatePartialLoader.addPart('question');
    $translatePartialLoader.addPart('multipleChoiceQuestion');
    $translate.refresh();

    var vm = this;

    vm.scoringTypeOptions = [
        {
            key: 1,
            label: "All or Nothing"
        },
        {
            key: 2,
            label: "Proportional Points for Correct Answer Options"
        },
        {
            key: 3,
            label: "True / False / No Answer"
        }
    ];

    vm.delete = function() {
        vm.onDelete();
    };
}

angular.module('artemisApp').component('multipleChoiceQuestion', {
    templateUrl: 'app/quiz/question/multiple-choice/multiple-choice-question.html',
    controller: MultipleChoiceQuestionController,
    controllerAs: 'vm',
    bindings: {
        question: '=',
        onDelete: '&'
    }
});
