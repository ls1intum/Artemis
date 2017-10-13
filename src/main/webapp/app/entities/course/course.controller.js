(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('CourseController', CourseController);

    CourseController.$inject = ['Course', 'ParseLinks', 'AlertService'];

    function CourseController(Course, ParseLinks, AlertService) {

        var vm = this;

        vm.courses = [];

        loadAll();

        function loadAll () {
            Course.query(function(result) {
                vm.courses = result;
                vm.searchQuery = null;
            });
        }

    }
})();
