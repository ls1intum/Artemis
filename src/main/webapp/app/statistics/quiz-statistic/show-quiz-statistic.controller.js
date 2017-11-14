(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ShowQuizStatisticController', ShowQuizStatisticController);

    ShowQuizStatisticController.$inject = ['$rootScope', '$scope', '$state', 'Principal', 'JhiWebsocketService', 'Statistic'];

    function ShowQuizStatisticController(rootScope, $scope, $state, Principal, JhiWebsocketService, Statistic) {

        var vm = this;


    }
})();
