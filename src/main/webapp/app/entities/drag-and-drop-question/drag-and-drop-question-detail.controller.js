(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragAndDropQuestionDetailController', DragAndDropQuestionDetailController);

    DragAndDropQuestionDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'DragAndDropQuestion', 'DropLocation', 'DragItem', 'DragAndDropAssignment'];

    function DragAndDropQuestionDetailController($scope, $rootScope, $stateParams, previousState, entity, DragAndDropQuestion, DropLocation, DragItem, DragAndDropAssignment) {
        var vm = this;

        vm.dragAndDropQuestion = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('artemisApp:dragAndDropQuestionUpdate', function(event, result) {
            vm.dragAndDropQuestion = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
