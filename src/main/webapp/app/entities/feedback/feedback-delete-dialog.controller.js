(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('FeedbackDeleteController',FeedbackDeleteController);

    FeedbackDeleteController.$inject = ['$uibModalInstance', 'entity', 'Feedback'];

    function FeedbackDeleteController($uibModalInstance, entity, Feedback) {
        var vm = this;

        vm.feedback = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            Feedback.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
