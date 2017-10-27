(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragAndDropStatisticDetailController', DragAndDropStatisticDetailController);

    DragAndDropStatisticDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'DragAndDropStatistic', 'DropLocationCounter'];

    function DragAndDropStatisticDetailController($scope, $rootScope, $stateParams, previousState, entity, DragAndDropStatistic, DropLocationCounter) {
        var vm = this;

        vm.dragAndDropStatistic = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('arTeMiSApp:dragAndDropStatisticUpdate', function(event, result) {
            vm.dragAndDropStatistic = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
