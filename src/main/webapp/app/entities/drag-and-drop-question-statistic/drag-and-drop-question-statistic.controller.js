(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragAndDropQuestionStatisticController', DragAndDropQuestionStatisticController);

    DragAndDropQuestionStatisticController.$inject = ['DragAndDropQuestionStatistic'];

    function DragAndDropQuestionStatisticController(DragAndDropQuestionStatistic) {

        var vm = this;

        vm.dragAndDropQuestionStatistics = [];

        loadAll();

        function loadAll() {
            DragAndDropQuestionStatistic.query(function(result) {
                vm.dragAndDropQuestionStatistics = result;
                vm.searchQuery = null;
            });
        }
    }
})();
