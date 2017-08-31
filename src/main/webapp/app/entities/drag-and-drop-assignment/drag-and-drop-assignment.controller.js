(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('DragAndDropAssignmentController', DragAndDropAssignmentController);

    DragAndDropAssignmentController.$inject = ['DragAndDropAssignment'];

    function DragAndDropAssignmentController(DragAndDropAssignment) {

        var vm = this;

        vm.dragAndDropAssignments = [];

        loadAll();

        function loadAll() {
            DragAndDropAssignment.query(function(result) {
                vm.dragAndDropAssignments = result;
                vm.searchQuery = null;
            });
        }
    }
})();
