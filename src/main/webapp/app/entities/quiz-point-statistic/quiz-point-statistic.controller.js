(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizPointStatisticController', QuizPointStatisticController);

    QuizPointStatisticController.$inject = ['QuizPointStatistic'];

    function QuizPointStatisticController(QuizPointStatistic) {

        var vm = this;

        vm.quizPointStatistics = [];

        loadAll();

        function loadAll() {
            QuizPointStatistic.query(function(result) {
                vm.quizPointStatistics = result;
                vm.searchQuery = null;
            });
        }
    }
})();
