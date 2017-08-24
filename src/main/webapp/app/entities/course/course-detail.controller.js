(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('CourseDetailController', CourseDetailController);

    CourseDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'Course', 'Exercise'];

    function CourseDetailController($scope, $rootScope, $stateParams, previousState, entity, Course, Exercise) {
        var vm = this;

        vm.course = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('exerciseApplicationApp:courseUpdate', function(event, result) {
            vm.course = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
