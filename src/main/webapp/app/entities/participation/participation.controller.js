(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ParticipationController', ParticipationController);

    ParticipationController.$inject = ['$scope', '$state', 'Participation'];

    function ParticipationController ($scope, $state, Participation) {
        var vm = this;
        
        vm.participations = [];

        loadAll();

        function getUniqueExercises() {
            var exercises = _.map(vm.participations, function (participation) {
                return participation.exercise;
            });
            vm.exercises = _.uniqBy(exercises, 'title');
        }

        function loadAll() {
            Participation.query(function(result) {
                vm.participations = result;
                getUniqueExercises();
            });
        }
    }
})();
