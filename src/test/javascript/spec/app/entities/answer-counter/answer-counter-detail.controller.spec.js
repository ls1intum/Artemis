'use strict';

describe('Controller Tests', function() {

    describe('AnswerCounter Management Detail Controller', function() {
        var $scope, $rootScope;
        var MockEntity, MockPreviousState, MockAnswerCounter, MockMultipleChoiceQuestionStatistic, MockAnswerOption;
        var createController;

        beforeEach(inject(function($injector) {
            $rootScope = $injector.get('$rootScope');
            $scope = $rootScope.$new();
            MockEntity = jasmine.createSpy('MockEntity');
            MockPreviousState = jasmine.createSpy('MockPreviousState');
            MockAnswerCounter = jasmine.createSpy('MockAnswerCounter');
            MockMultipleChoiceQuestionStatistic = jasmine.createSpy('MockMultipleChoiceQuestionStatistic');
            MockAnswerOption = jasmine.createSpy('MockAnswerOption');


            var locals = {
                '$scope': $scope,
                '$rootScope': $rootScope,
                'entity': MockEntity,
                'previousState': MockPreviousState,
                'AnswerCounter': MockAnswerCounter,
                'MultipleChoiceQuestionStatistic': MockMultipleChoiceQuestionStatistic,
                'AnswerOption': MockAnswerOption
            };
            createController = function() {
                $injector.get('$controller')("AnswerCounterDetailController", locals);
            };
        }));


        describe('Root Scope Listening', function() {
            it('Unregisters root scope listener upon scope destruction', function() {
                var eventType = 'artemisApp:answerCounterUpdate';

                createController();
                expect($rootScope.$$listenerCount[eventType]).toEqual(1);

                $scope.$destroy();
                expect($rootScope.$$listenerCount[eventType]).toBeUndefined();
            });
        });
    });

});
