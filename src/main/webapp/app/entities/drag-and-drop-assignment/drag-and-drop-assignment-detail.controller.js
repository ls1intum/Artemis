(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragAndDropAssignmentDetailController', DragAndDropAssignmentDetailController);

    DragAndDropAssignmentDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'DragAndDropAssignment', 'DragItem', 'DropLocation', 'DragAndDropSubmittedAnswer'];

    function DragAndDropAssignmentDetailController($scope, $rootScope, $stateParams, previousState, entity, DragAndDropAssignment, DragItem, DropLocation, DragAndDropSubmittedAnswer) {
        var vm = this;

        vm.dragAndDropAssignment = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('artemisApp:dragAndDropAssignmentUpdate', function(event, result) {
            vm.dragAndDropAssignment = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
