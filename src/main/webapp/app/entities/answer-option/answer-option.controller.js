(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('AnswerOptionController', AnswerOptionController);

    AnswerOptionController.$inject = ['AnswerOption'];

    function AnswerOptionController(AnswerOption) {

        var vm = this;

        vm.answerOptions = [];

        loadAll();

        function loadAll() {
            AnswerOption.query(function(result) {
                vm.answerOptions = result;
                vm.searchQuery = null;
            });
        }
    }
})();
