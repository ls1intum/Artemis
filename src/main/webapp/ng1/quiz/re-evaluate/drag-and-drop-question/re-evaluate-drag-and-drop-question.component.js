angular.module('artemisApp').component('reEvaluateDragAndDropQuestion', {
    templateUrl: 'ng1/quiz/re-evaluate/drag-and-drop-question/re-evaluate-drag-and-drop-question.html',
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
