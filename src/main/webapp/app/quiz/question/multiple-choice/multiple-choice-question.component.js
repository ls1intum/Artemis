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

    /**
     * watch for any changes to the question model and notify listener
     *
     * (use 'initializing' boolean to prevent $watch from firing immediately)
     */
    var initializing = true;
    $scope.$watchCollection('vm.question', function() {
        if(initializing) {
            initializing = false;
            return;
        }
        vm.onUpdated();
    });

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
        onDelete: '&',
        onUpdated: '&'
    }
});
