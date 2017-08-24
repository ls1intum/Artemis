(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ParticipationController', ParticipationController);

    ParticipationController.$inject = ['$scope', '$state', 'Participation', 'ExerciseParticipations', 'exerciseEntity'];

    function ParticipationController ($scope, $state, Participation, ExerciseParticipations,  exerciseEntity) {
        var vm = this;

        vm.participations = [];

        vm.exercise = exerciseEntity;

        load();

        function getUniqueExercises() {
            var exercises = _.map(vm.participations, function (participation) {
                return participation.exercise;
            });
            vm.exercises = _.uniqBy(exercises, 'title');
        }

        function load() {
            if(vm.exercise) {
                loadForExercise(vm.exercise);
            } else {
                loadAll();
            }
        }

        function loadAll() {
            Participation.query(function(result) {
                vm.participations = result;
                vm.searchQuery = null;
            });
        }

        function loadForExercise(exercise) {
            ExerciseParticipations.query({exerciseId: exercise.id},function(result) {
                vm.participations = result;
            });
        }

    }
})();
