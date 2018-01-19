(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuestionStatisticDetailController', QuestionStatisticDetailController);

    QuestionStatisticDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'QuestionStatistic', 'Question'];

    function QuestionStatisticDetailController($scope, $rootScope, $stateParams, previousState, entity, QuestionStatistic, Question) {
        var vm = this;

        vm.questionStatistic = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('artemisApp:questionStatisticUpdate', function(event, result) {
            vm.questionStatistic = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
