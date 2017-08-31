(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ModelingSubmissionController', ModelingSubmissionController);

    ModelingSubmissionController.$inject = ['ModelingSubmission'];

    function ModelingSubmissionController(ModelingSubmission) {

        var vm = this;

        vm.modelingSubmissions = [];

        loadAll();

        function loadAll() {
            ModelingSubmission.query(function(result) {
                vm.modelingSubmissions = result;
                vm.searchQuery = null;
            });
        }
    }
})();
