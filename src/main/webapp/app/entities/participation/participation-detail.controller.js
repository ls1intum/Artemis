(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ParticipationDetailController', ParticipationDetailController);

    ParticipationDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'entity', 'Participation', 'User', 'Result'];

    function ParticipationDetailController($scope, $rootScope, $stateParams, entity, Participation, User, Result) {
        var vm = this;

        vm.participation = entity;

        var unsubscribe = $rootScope.$on('exerciseApplicationApp:participationUpdate', function(event, result) {
            vm.participation = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
