(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ProgrammingExerciseController', ProgrammingExerciseController);

    ProgrammingExerciseController.$inject = ['ProgrammingExercise'];

    function ProgrammingExerciseController(ProgrammingExercise) {

        var vm = this;

        vm.programmingExercises = [];

        loadAll();

        function loadAll() {
            ProgrammingExercise.query(function(result) {
                vm.programmingExercises = result;
                vm.searchQuery = null;
            });
        }
    }
})();
