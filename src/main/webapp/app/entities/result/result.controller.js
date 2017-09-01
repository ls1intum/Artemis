(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ResultController', ResultController);

    ResultController.$inject = ['Result'];

    function ResultController(Result) {

        var vm = this;

        vm.results = [];

        loadAll();

        function loadAll() {
            Result.query(function(result) {
                vm.results = result;
                vm.searchQuery = null;
            });
        }
    }
})();
