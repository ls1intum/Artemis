angular
    .module('artemisApp')
    .controller('TutorialCourseController', TutorialCourseController);

TutorialCourseController.$inject = ['$scope', '$q', '$cookies', 'Modal', 'Course', 'uiTourService', 'CourseExercises'];

function TutorialCourseController($scope, $q, $cookies, Modal, Course, uiTourService, CourseExercises) {

    var self = this;

    self.loaded=false;
    self.course = {};

    loadTutorial();
    askForTutorial();

    function askForTutorial() {
        var tutorialState = $cookies.get("tutorialDone");

        if (!tutorialState) {
            var modalOptions = {
                closeButtonText: 'Skip',
                actionsButtonText: 'Do Tutorial!',
                headerText: 'Tutorial?',
                bodyText: 'Since this is your first time on Artemis, do you wish to take a guided Tutorial?'
            };

            Modal.showModal({}, modalOptions).then(function (result) {
                if (result.result == 'ok') {
                   $cookies.put("tutorialDone", 'started');
                    tutorialState = 'started';
                    self.tour = uiTourService.getTour();
                    self.tour.start();
                } else {
                    $cookies.put("tutorialDone", 'skipped');
                    tutorialState = 'skipped';
                }
            });
        }

        switch(tutorialState){
            case 'skipped':
                self.skipped = true;
                break;
            case 'finished':
                self.finished = true;
                break;
            default:
                self.started = true;
                break;
        }

    }

    function loadTutorial() {
        Course.query().$promise.then(function (courses) {
            self.course = _.first(courses);
            self.loaded=true;
        });
    };

}
