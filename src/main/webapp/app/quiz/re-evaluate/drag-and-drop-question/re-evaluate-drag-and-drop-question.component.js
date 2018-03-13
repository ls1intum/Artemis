angular.module('artemisApp').component('reEvaluateDragAndDropQuestion', {
    templateUrl: 'app/quiz/re-evaluate/drag-and-drop-question/re-evaluate-drag-and-drop-question.html',
    controller: "EditDragAndDropQuestionController",
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
