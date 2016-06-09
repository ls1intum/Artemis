(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('CourseDeleteController',CourseDeleteController);

    CourseDeleteController.$inject = ['$uibModalInstance', 'entity', 'Course'];

    function CourseDeleteController($uibModalInstance, entity, Course) {
        var vm = this;

        vm.course = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;
        
        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            Course.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
