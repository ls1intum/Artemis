(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('MultipleChoiceStatisticDetailController', MultipleChoiceStatisticDetailController);

    MultipleChoiceStatisticDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'MultipleChoiceStatistic', 'AnswerCounter'];

    function MultipleChoiceStatisticDetailController($scope, $rootScope, $stateParams, previousState, entity, MultipleChoiceStatistic, AnswerCounter) {
        var vm = this;

        vm.multipleChoiceStatistic = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('arTeMiSApp:multipleChoiceStatisticUpdate', function(event, result) {
            vm.multipleChoiceStatistic = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
