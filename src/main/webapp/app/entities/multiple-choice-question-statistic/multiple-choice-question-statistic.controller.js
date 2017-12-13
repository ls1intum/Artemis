(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('MultipleChoiceQuestionStatisticController', MultipleChoiceQuestionStatisticController);

    MultipleChoiceQuestionStatisticController.$inject = ['MultipleChoiceQuestionStatistic'];

    function MultipleChoiceQuestionStatisticController(MultipleChoiceQuestionStatistic) {

        var vm = this;

        vm.multipleChoiceQuestionStatistics = [];

        loadAll();

        function loadAll() {
            MultipleChoiceQuestionStatistic.query(function(result) {
                vm.multipleChoiceQuestionStatistics = result;
                vm.searchQuery = null;
            });
        }
    }
})();
