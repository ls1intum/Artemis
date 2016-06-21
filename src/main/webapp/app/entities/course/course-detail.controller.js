(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('CourseDetailController', CourseDetailController);

    CourseDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'entity', 'Course', 'Exercise'];

    function CourseDetailController($scope, $rootScope, $stateParams, entity, Course, Exercise) {
        var vm = this;

        vm.course = entity;

        var unsubscribe = $rootScope.$on('exerciseApplicationApp:courseUpdate', function(event, result) {
            vm.course = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
