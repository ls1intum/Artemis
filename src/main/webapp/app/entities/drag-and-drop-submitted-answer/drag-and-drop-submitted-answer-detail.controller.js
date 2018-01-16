(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragAndDropSubmittedAnswerDetailController', DragAndDropSubmittedAnswerDetailController);

    DragAndDropSubmittedAnswerDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'DragAndDropSubmittedAnswer', 'DragAndDropMapping'];

    function DragAndDropSubmittedAnswerDetailController($scope, $rootScope, $stateParams, previousState, entity, DragAndDropSubmittedAnswer, DragAndDropMapping) {
        var vm = this;

        vm.dragAndDropSubmittedAnswer = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('artemisApp:dragAndDropSubmittedAnswerUpdate', function(event, result) {
            vm.dragAndDropSubmittedAnswer = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
