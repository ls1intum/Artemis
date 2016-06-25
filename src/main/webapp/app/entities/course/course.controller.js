(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('CourseController', CourseController);

    CourseController.$inject = ['$scope', '$state', 'Course'];

    function CourseController ($scope, $state, Course) {
        var vm = this;
        
        vm.courses = [];

        loadAll();

        function loadAll() {
            Course.query(function(result) {
                vm.courses = result;
            });
        }
    }
})();
