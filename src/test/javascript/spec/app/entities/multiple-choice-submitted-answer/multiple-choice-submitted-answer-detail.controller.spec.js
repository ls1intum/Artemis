'use strict';

describe('Controller Tests', function() {

    describe('MultipleChoiceSubmittedAnswer Management Detail Controller', function() {
        var $scope, $rootScope;
        var MockEntity, MockPreviousState, MockMultipleChoiceSubmittedAnswer, MockAnswerOption;
        var createController;

        beforeEach(inject(function($injector) {
            $rootScope = $injector.get('$rootScope');
            $scope = $rootScope.$new();
            MockEntity = jasmine.createSpy('MockEntity');
            MockPreviousState = jasmine.createSpy('MockPreviousState');
            MockMultipleChoiceSubmittedAnswer = jasmine.createSpy('MockMultipleChoiceSubmittedAnswer');
            MockAnswerOption = jasmine.createSpy('MockAnswerOption');
            

            var locals = {
                '$scope': $scope,
                '$rootScope': $rootScope,
                'entity': MockEntity,
                'previousState': MockPreviousState,
                'MultipleChoiceSubmittedAnswer': MockMultipleChoiceSubmittedAnswer,
                'AnswerOption': MockAnswerOption
            };
            createController = function() {
                $injector.get('$controller')("MultipleChoiceSubmittedAnswerDetailController", locals);
            };
        }));


        describe('Root Scope Listening', function() {
            it('Unregisters root scope listener upon scope destruction', function() {
                var eventType = 'artemisApp:multipleChoiceSubmittedAnswerUpdate';

                createController();
                expect($rootScope.$$listenerCount[eventType]).toEqual(1);

                $scope.$destroy();
                expect($rootScope.$$listenerCount[eventType]).toBeUndefined();
            });
        });
    });

});
