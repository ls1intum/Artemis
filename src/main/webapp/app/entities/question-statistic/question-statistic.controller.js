(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuestionStatisticController', QuestionStatisticController);

    QuestionStatisticController.$inject = ['QuestionStatistic'];

    function QuestionStatisticController(QuestionStatistic) {

        var vm = this;

        vm.questionStatistics = [];

        loadAll();

        function loadAll() {
            QuestionStatistic.query(function(result) {
                vm.questionStatistics = result;
                vm.searchQuery = null;
            });
        }
    }
})();
