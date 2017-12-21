angular.module('artemisApp')
    .directive('tutortialCourse', tutorialCourse);

function tutorialCourse(){
    return{
        restrict: 'E',
        templateUrl: 'app/tutorial/tutorialCourse.html',
        controller: 'TutorialCourseController',
        controllerAs: 'tutorial'
    };
};
