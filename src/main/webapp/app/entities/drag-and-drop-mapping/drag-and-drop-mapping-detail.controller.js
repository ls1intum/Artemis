(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragAndDropMappingDetailController', DragAndDropMappingDetailController);

    DragAndDropMappingDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'DragAndDropMapping', 'DragItem', 'DropLocation', 'DragAndDropSubmittedAnswer', 'DragAndDropQuestion'];

    function DragAndDropMappingDetailController($scope, $rootScope, $stateParams, previousState, entity, DragAndDropMapping, DragItem, DropLocation, DragAndDropSubmittedAnswer, DragAndDropQuestion) {
        var vm = this;

        vm.dragAndDropMapping = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('artemisApp:dragAndDropMappingUpdate', function(event, result) {
            vm.dragAndDropMapping = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
