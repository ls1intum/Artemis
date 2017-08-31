(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('DragAndDropAssignmentDetailController', DragAndDropAssignmentDetailController);

    DragAndDropAssignmentDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'DragAndDropAssignment', 'DragItem', 'DropLocation', 'DragAndDropSubmittedAnswer'];

    function DragAndDropAssignmentDetailController($scope, $rootScope, $stateParams, previousState, entity, DragAndDropAssignment, DragItem, DropLocation, DragAndDropSubmittedAnswer) {
        var vm = this;

        vm.dragAndDropAssignment = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('exerciseApplicationApp:dragAndDropAssignmentUpdate', function(event, result) {
            vm.dragAndDropAssignment = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
