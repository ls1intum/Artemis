(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('AnswerCounterController', AnswerCounterController);

    AnswerCounterController.$inject = ['AnswerCounter'];

    function AnswerCounterController(AnswerCounter) {

        var vm = this;

        vm.answerCounters = [];

        loadAll();

        function loadAll() {
            AnswerCounter.query(function(result) {
                vm.answerCounters = result;
                vm.searchQuery = null;
            });
        }
    }
})();
