angular.module('artemisApp')
    .controller('TutorialCourseController', TutorialCourseController);

TutorialCourseController.$inject = ['$scope', '$q', 'Modal', 'Course', 'Cookie', 'uiTourService'];

function TutorialCourseController($scope, $q, Modal, Course, Cookie, uiTourService){

    var self = this;

    loadTutorial();
    askForTutorial();

    function askForTutorial() {
        var tutorialState = Cookie.getFromCookie("tutorialDone");

        if(tutorialState == 'false' || tutorialState=="") {
            var modalOptions = {
                closeButtonText: 'Skip',
                actionsButtonText: 'Do Tutorial!',
                headerText: 'Tutorial?',
                bodyText: 'Since this is your first time on Artemis, do you wish to take a guided Tutorial?'
            };

            Modal.showModal({}, modalOptions).then(function (result) {
                if(result.result == 'ok'){
                    //Cookie.setInCookie("tutorialDone", 'started', 365);
                    var tour = uiTourService.getTour();
                    tour.start();
                }
            });
        }
    }

   function loadTutorial(){
       Course.query().$promise.then(function(courses){
           self.course = _.first(courses);

       });

   };

}
