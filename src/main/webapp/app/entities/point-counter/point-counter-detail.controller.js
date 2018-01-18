(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('PointCounterDetailController', PointCounterDetailController);

    PointCounterDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'PointCounter', 'QuizPointStatistic'];

    function PointCounterDetailController($scope, $rootScope, $stateParams, previousState, entity, PointCounter, QuizPointStatistic) {
        var vm = this;

        vm.pointCounter = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('artemisApp:pointCounterUpdate', function(event, result) {
            vm.pointCounter = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
