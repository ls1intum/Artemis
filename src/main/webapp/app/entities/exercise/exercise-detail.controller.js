(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ExerciseDetailController', ExerciseDetailController);

    ExerciseDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'Exercise', 'Course', 'Participation'];

    function ExerciseDetailController($scope, $rootScope, $stateParams, previousState, entity, Exercise, Course, Participation) {
        var vm = this;

        vm.exercise = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('exerciseApplicationApp:exerciseUpdate', function(event, result) {
            vm.exercise = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
