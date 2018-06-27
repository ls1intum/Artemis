(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('CourseController', CourseController);

    CourseController.$inject = ['Course', 'ParseLinks', 'AlertService'];

    function CourseController(Course, ParseLinks, AlertService) {

        var vm = this;

        vm.courses = [];
        vm.sort = sort;

        loadAll();

        function loadAll() {
            Course.query(function(result) {
                vm.courses = result;
                vm.searchQuery = null;
            });
        }

        function sort() {
            vm.courses.sort(function (a, b) {
                var result = (a[vm.predicate] < b[vm.predicate]) ? -1 : (a[vm.predicate] > b[vm.predicate]) ? 1 : (
                    (a.id < b.id) ? -1 : (a.id > b.id) ? 1 : 0
                );
                return result * (vm.reverse ? -1 : 1);
            });
        }
    }
})();
