(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ParticipationController', ParticipationController);

    ParticipationController.$inject = ['Participation'];

    function ParticipationController(Participation) {

        var vm = this;

        vm.participations = [];

        loadAll();

        function loadAll() {
            Participation.query(function(result) {
                vm.participations = result;
                vm.searchQuery = null;
            });
        }
    }
})();
