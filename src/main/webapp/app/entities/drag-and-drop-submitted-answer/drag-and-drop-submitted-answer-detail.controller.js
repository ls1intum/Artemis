(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragAndDropSubmittedAnswerDetailController', DragAndDropSubmittedAnswerDetailController);

    DragAndDropSubmittedAnswerDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'DragAndDropSubmittedAnswer', 'DragAndDropAssignment'];

    function DragAndDropSubmittedAnswerDetailController($scope, $rootScope, $stateParams, previousState, entity, DragAndDropSubmittedAnswer, DragAndDropAssignment) {
        var vm = this;

        vm.dragAndDropSubmittedAnswer = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('artemisApp:dragAndDropSubmittedAnswerUpdate', function(event, result) {
            vm.dragAndDropSubmittedAnswer = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
