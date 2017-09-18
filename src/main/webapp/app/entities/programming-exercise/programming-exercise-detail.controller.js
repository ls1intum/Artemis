(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ProgrammingExerciseDetailController', ProgrammingExerciseDetailController);

    ProgrammingExerciseDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'ProgrammingExercise'];

    function ProgrammingExerciseDetailController($scope, $rootScope, $stateParams, previousState, entity, ProgrammingExercise) {
        var vm = this;

        vm.programmingExercise = entity;
        vm.previousState = previousState.url;

        var unsubscribe = $rootScope.$on('artemisApp:programmingExerciseUpdate', function(event, result) {
            vm.programmingExercise = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
