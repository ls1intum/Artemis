(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ParticipationController', ParticipationController);

    ParticipationController.$inject = ['$scope', '$state', 'Participation', 'ParticipationSearch'];

    function ParticipationController ($scope, $state, Participation, ParticipationSearch) {
        var vm = this;
        
        vm.participations = [];
        vm.search = search;

        loadAll();

        function loadAll() {
            Participation.query(function(result) {
                vm.participations = result;
            });
        }

        function search () {
            if (!vm.searchQuery) {
                return vm.loadAll();
            }
            ParticipationSearch.query({query: vm.searchQuery}, function(result) {
                vm.participations = result;
            });
        }    }
})();
