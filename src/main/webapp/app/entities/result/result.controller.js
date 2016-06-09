(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ResultController', ResultController);

    ResultController.$inject = ['$scope', '$state', 'Result', 'ResultSearch'];

    function ResultController ($scope, $state, Result, ResultSearch) {
        var vm = this;
        
        vm.results = [];
        vm.search = search;

        loadAll();

        function loadAll() {
            Result.query(function(result) {
                vm.results = result;
            });
        }

        function search () {
            if (!vm.searchQuery) {
                return vm.loadAll();
            }
            ResultSearch.query({query: vm.searchQuery}, function(result) {
                vm.results = result;
            });
        }    }
})();
