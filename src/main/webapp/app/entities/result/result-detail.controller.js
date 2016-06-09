(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ResultDetailController', ResultDetailController);

    ResultDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'entity', 'Result', 'Participation'];

    function ResultDetailController($scope, $rootScope, $stateParams, entity, Result, Participation) {
        var vm = this;

        vm.result = entity;

        var unsubscribe = $rootScope.$on('exerciseApplicationApp:resultUpdate', function(event, result) {
            vm.result = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
