(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragItemDetailController', DragItemDetailController);

    DragItemDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'DragItem', 'DropLocation', 'DragAndDropQuestion'];

    function DragItemDetailController($scope, $rootScope, $stateParams, previousState, entity, DragItem, DropLocation, DragAndDropQuestion) {
        var vm = this;

        vm.dragItem = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('artemisApp:dragItemUpdate', function(event, result) {
            vm.dragItem = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
