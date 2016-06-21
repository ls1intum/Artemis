(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ParticipationController', ParticipationController);

    ParticipationController.$inject = ['$scope', '$state', 'Participation'];

    function ParticipationController ($scope, $state, Participation) {
        var vm = this;
        
        vm.participations = [];

        loadAll();

        function loadAll() {
            Participation.query(function(result) {
                vm.participations = result;
            });
        }
    }
})();
