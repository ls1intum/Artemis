(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('SubmissionController', SubmissionController);

    SubmissionController.$inject = ['Submission'];

    function SubmissionController(Submission) {

        var vm = this;

        vm.submissions = [];

        loadAll();

        function loadAll() {
            Submission.query(function(result) {
                vm.submissions = result;
                vm.searchQuery = null;
            });
        }
    }
})();
