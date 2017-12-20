(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragAndDropQuestionStatisticDetailController', DragAndDropQuestionStatisticDetailController);

    DragAndDropQuestionStatisticDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'DragAndDropQuestionStatistic', 'DropLocationCounter'];

    function DragAndDropQuestionStatisticDetailController($scope, $rootScope, $stateParams, previousState, entity, DragAndDropQuestionStatistic, DropLocationCounter) {
        var vm = this;

        vm.dragAndDropQuestionStatistic = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('arTeMiSApp:dragAndDropQuestionStatisticUpdate', function(event, result) {
            vm.dragAndDropQuestionStatistic = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
