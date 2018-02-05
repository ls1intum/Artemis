angular
    .module('artemisApp')
    .controller('TutorialCourseController', TutorialCourseController);

TutorialCourseController.$inject = ['$scope', '$q', '$cookies', 'Modal', 'Course', 'uiTourService', 'CourseExercises'];

function TutorialCourseController($scope, $q, $cookies, Modal, Course, uiTourService, CourseExercises) {

    var self = this;

    self.loaded = false;
    self.do = false;
    self.course = {};
    self.tour = {};
    self.exercise = {};
    self.amountOfExercises = 0;

    init();


    function init() {
        loadTutorial();
        askForTutorial();
    }

    function doTutorial(step, name) {
        self.do = true;

        var tour = {};

        if(name){
            tour = uiTourService.getTourByName(name);
        } else {
            tour = uiTourService.getTour();
        }

        if (step) {
            tour.waitFor(step);
            tour.startAt(step);
        } else {
            tour.start();
        }

        tour.on('started', function (data) {
            var stepID = $cookies.getObject('tutorialStep');

            if(stepID) {
                //tour.goTo(stepID);
            }
        });

        tour.on('ended', function (data) {
            $cookies.put("tutorialDone", 'finished');
            $cookies.remove('tutorialStep');
            self.do = false;
        });

        tour.on('paused', function (data) {
            console.log(data);
            $cookies.putObject('tutorialStep', data.order);
            $cookies.put("tutorialDone", 'skipped');
        });

        tour.on('stepChanged', function (data) {
            if(data.order == "31"){
                $cookies.putObject('tutorialEditor', true);
            }
        });

    };


    function askForTutorial() {
        var tutorialState = $cookies.get("tutorialDone");
        var inEditor = $cookies.get("tutorialEditor");

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
                    doTutorial();
                } else {
                    $cookies.put("tutorialDone", 'skipped');
                    tutorialState = 'skipped';
                }
            });
        }

        switch (tutorialState) {
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
            self.loaded = true;
            loadExercises();
        });
    };

    function loadExercises() {
        CourseExercises.query({
            courseId: self.course.id,
            withLtiOutcomeUrlExisting: true
        }).$promise.then(function (exercises) {
            self.exercise = exercises;
            self.amountOfExercises = _.size(self.exercise);

            var inEditor = $cookies.get("tutorialEditor");
            if(inEditor){
                self.skipped=true;
                doTutorial('32');
            }
        });
    }

    self.startTutorial = function () {
        doTutorial();
    };

    self.continueTutorial = function () {
        self.do = true;

        if(self.loaded){
            doTutorial('' + $cookies.getObject('tutorialStep'));
        }else{
            doTutorial();
        }
    };

    self.startTutorialAt = function (tutorialStepId) {
       doTutorial(tutorialStepId);
    };


    self.getFilterId = function getFilterId(exercisePos) {
        return _.toArray(self.exercise)[exercisePos].id;
    }
}
