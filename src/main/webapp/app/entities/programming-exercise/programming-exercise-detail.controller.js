(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ProgrammingExerciseDetailController', ProgrammingExerciseDetailController);

    ProgrammingExerciseDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'ProgrammingExercise'];

    function ProgrammingExerciseDetailController($scope, $rootScope, $stateParams, previousState, entity, ProgrammingExercise) {
        var vm = this;

        vm.programmingExercise = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('exerciseApplicationApp:programmingExerciseUpdate', function(event, result) {
            vm.programmingExercise = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
