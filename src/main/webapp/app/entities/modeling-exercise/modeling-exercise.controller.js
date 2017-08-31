(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ModelingExerciseController', ModelingExerciseController);

    ModelingExerciseController.$inject = ['ModelingExercise'];

    function ModelingExerciseController(ModelingExercise) {

        var vm = this;

        vm.modelingExercises = [];

        loadAll();

        function loadAll() {
            ModelingExercise.query(function(result) {
                vm.modelingExercises = result;
                vm.searchQuery = null;
            });
        }
    }
})();
