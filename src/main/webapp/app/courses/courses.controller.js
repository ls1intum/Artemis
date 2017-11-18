(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('CoursesController', CoursesController);

    CoursesController.$inject = ['$scope', '$q', '$state', 'Course', 'CourseExercises'];

    function CoursesController($scope, $q, $state, Course, CourseExercises) {
        var vm = this;

        vm.filterByCourseId = _.toInteger(_.get($state,"params.courseId"));
        vm.filterByExerciseId = _.toInteger(_.get($state,"params.exerciseId"));

        loadAll();
        openNav();

        function loadAll() {
            Course.query().$promise.then(function (courses) {

                vm.courses = courses;

                if(vm.filterByCourseId) {
                    vm.courses = _.filter(vm.courses, {
                        'id': vm.filterByCourseId
                    });
                }
            });
        }

        /* Open overlay*/
        function openNav() {
            if(getSeenInCookie()){
                return;
            }
            setSeenInCookie("true");
            document.getElementById("WelcomeOverlay").style.display = "block";
        }

        /* Close overlay*/
        $scope.closeNav = function() {
            console.log("closed");
            document.getElementById("WelcomeOverlay").style.display = "none";
        }

        /* set in cookie if overlay has been closed */
        function setSeenInCookie(bool){
            var date = new Date();
            date.setTime(date.getTime() + 365*24*60*60*1000);
            var expires = "expires=" + date.toUTCString();
            document.cookie = "tutorialDone" +  "=" + bool + ";" + expires + ";path=/"
        }

        /* get the seen state frome the cookie */
        function getSeenInCookie() {
            var name = "tutorialDone=";
            var decodedCookie = decodeURIComponent(document.cookie);
            var cookieEntries = decodedCookie.split(';');

            for(var i=0; i<cookieEntries.length; i++){
                var entry = cookieEntries[i];

                while(entry.charAt(0) == ' '){
                    entry = entry.substring(1);
                }
                if(entry.indexOf(name) == 0){
                    return entry.substring(name.length, entry.length) == "true";
                }
            }
            return false;
        }

    }
})();
