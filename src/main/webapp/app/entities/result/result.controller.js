(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ResultController', ResultController);

    ResultController.$inject = ['$scope', '$state', 'Result'];

    function ResultController ($scope, $state, Result) {
        var vm = this;
        
        vm.results = [];

        loadAll();

        function loadAll() {
            Result.query(function(result) {
                vm.results = result;
            });
        }
    }
})();
