(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('CoursesController', CoursesController);

    CoursesController.$inject = ['$scope', '$q', '$state', 'Course', '$http', 'Principal'];

    function CoursesController($scope, $q, $state, Course, $http, Principal) {
        var vm = this;

        vm.filterByCourseId = _.toInteger(_.get($state, "params.courseId"));
        vm.filterByExerciseId = _.toInteger(_.get($state, "params.exerciseId"));

        loadAll();

        function loadAll() {
            Course.query({id: "for-dashboard"}).$promise.then(function (courses) {

                vm.courses = courses;

                if (vm.filterByCourseId) {
                    vm.courses = _.filter(vm.courses, {
                        'id': vm.filterByCourseId
                    });
                }
            });
        }

        vm.repositoryPassword = null;
        Principal.identity().then(function (account) {
            // only load password if current user login starts with "edx"
            if (account && account.login && account.login.startsWith("edx")) {
                return getRepositoryPassword();
            } else {
                return null;
            }
        }).then(function(result) {
            vm.repositoryPassword = result;
        });

        function getRepositoryPassword() {
            return $http.get('api/account/password', {
                ignoreLoadingBar: true
            }).then(function (response) {
                if (response.data && response.data.password && response.data.password !== "") {
                    return response.data.password;
                } else {
                    return null;
                }
            }).catch(function () {
                return null;
            });
        }
    }
})();
