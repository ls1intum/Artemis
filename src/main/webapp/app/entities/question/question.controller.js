(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('QuestionController', QuestionController);

    QuestionController.$inject = ['Question'];

    function QuestionController(Question) {

        var vm = this;

        vm.questions = [];

        loadAll();

        function loadAll() {
            Question.query(function(result) {
                vm.questions = result;
                vm.searchQuery = null;
            });
        }
    }
})();
