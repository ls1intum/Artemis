(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('LtiUserIdController', LtiUserIdController);

    LtiUserIdController.$inject = ['LtiUserId'];

    function LtiUserIdController(LtiUserId) {

        var vm = this;

        vm.ltiUserIds = [];

        loadAll();

        function loadAll() {
            LtiUserId.query(function(result) {
                vm.ltiUserIds = result;
                vm.searchQuery = null;
            });
        }
    }
})();
