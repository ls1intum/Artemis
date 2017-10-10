(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('FeedbackController', FeedbackController);

    FeedbackController.$inject = ['Feedback'];

    function FeedbackController(Feedback) {

        var vm = this;

        vm.feedbacks = [];

        loadAll();

        function loadAll() {
            Feedback.query(function(result) {
                vm.feedbacks = result;
                vm.searchQuery = null;
            });
        }
    }
})();
