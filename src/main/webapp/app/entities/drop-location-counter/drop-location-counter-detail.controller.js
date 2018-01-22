(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DropLocationCounterDetailController', DropLocationCounterDetailController);

    DropLocationCounterDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'DropLocationCounter', 'DragAndDropQuestionStatistic', 'DropLocation'];

    function DropLocationCounterDetailController($scope, $rootScope, $stateParams, previousState, entity, DropLocationCounter, DragAndDropQuestionStatistic, DropLocation) {
        var vm = this;

        vm.dropLocationCounter = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('artemisApp:dropLocationCounterUpdate', function(event, result) {
            vm.dropLocationCounter = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
