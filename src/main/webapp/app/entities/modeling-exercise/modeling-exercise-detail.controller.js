(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ModelingExerciseDetailController', ModelingExerciseDetailController);

    ModelingExerciseDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'ModelingExercise'];

    function ModelingExerciseDetailController($scope, $rootScope, $stateParams, previousState, entity, ModelingExercise) {
        var vm = this;

        vm.modelingExercise = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('exerciseApplicationApp:modelingExerciseUpdate', function(event, result) {
            vm.modelingExercise = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
