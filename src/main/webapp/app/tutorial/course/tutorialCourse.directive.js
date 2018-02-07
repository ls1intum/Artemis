angular.module('artemisApp')
    .directive('tutorialCourse', tutorialCourse);

function tutorialCourse(){
    return{
        restrict: 'E',
        templateUrl: 'app/tutorial/course/tutorialCourse.html',
        controller: 'TutorialCourseController',
        controllerAs: 'tutorial'
    };
};
