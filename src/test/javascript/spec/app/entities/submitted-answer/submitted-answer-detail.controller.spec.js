'use strict';

describe('Controller Tests', function() {

    describe('SubmittedAnswer Management Detail Controller', function() {
        var $scope, $rootScope;
        var MockEntity, MockPreviousState, MockSubmittedAnswer, MockQuestion, MockQuizSubmission;
        var createController;

        beforeEach(inject(function($injector) {
            $rootScope = $injector.get('$rootScope');
            $scope = $rootScope.$new();
            MockEntity = jasmine.createSpy('MockEntity');
            MockPreviousState = jasmine.createSpy('MockPreviousState');
            MockSubmittedAnswer = jasmine.createSpy('MockSubmittedAnswer');
            MockQuestion = jasmine.createSpy('MockQuestion');
            MockQuizSubmission = jasmine.createSpy('MockQuizSubmission');
            

            var locals = {
                '$scope': $scope,
                '$rootScope': $rootScope,
                'entity': MockEntity,
                'previousState': MockPreviousState,
                'SubmittedAnswer': MockSubmittedAnswer,
                'Question': MockQuestion,
                'QuizSubmission': MockQuizSubmission
            };
            createController = function() {
                $injector.get('$controller')("SubmittedAnswerDetailController", locals);
            };
        }));


        describe('Root Scope Listening', function() {
            it('Unregisters root scope listener upon scope destruction', function() {
                var eventType = 'artemisApp:submittedAnswerUpdate';

                createController();
                expect($rootScope.$$listenerCount[eventType]).toEqual(1);

                $scope.$destroy();
                expect($rootScope.$$listenerCount[eventType]).toBeUndefined();
            });
        });
    });

});
