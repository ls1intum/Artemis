'use strict';

describe('Controller Tests', function() {

    describe('MultipleChoiceQuestionStatistic Management Detail Controller', function() {
        var $scope, $rootScope;
        var MockEntity, MockPreviousState, MockMultipleChoiceQuestionStatistic, MockAnswerCounter;
        var createController;

        beforeEach(inject(function($injector) {
            $rootScope = $injector.get('$rootScope');
            $scope = $rootScope.$new();
            MockEntity = jasmine.createSpy('MockEntity');
            MockPreviousState = jasmine.createSpy('MockPreviousState');
            MockMultipleChoiceQuestionStatistic = jasmine.createSpy('MockMultipleChoiceQuestionStatistic');
            MockAnswerCounter = jasmine.createSpy('MockAnswerCounter');


            var locals = {
                '$scope': $scope,
                '$rootScope': $rootScope,
                'entity': MockEntity,
                'previousState': MockPreviousState,
                'MultipleChoiceQuestionStatistic': MockMultipleChoiceQuestionStatistic,
                'AnswerCounter': MockAnswerCounter
            };
            createController = function() {
                $injector.get('$controller')("MultipleChoiceQuestionStatisticDetailController", locals);
            };
        }));


        describe('Root Scope Listening', function() {
            it('Unregisters root scope listener upon scope destruction', function() {
                var eventType = 'artemisApp:multipleChoiceQuestionStatisticUpdate';

                createController();
                expect($rootScope.$$listenerCount[eventType]).toEqual(1);

                $scope.$destroy();
                expect($rootScope.$$listenerCount[eventType]).toBeUndefined();
            });
        });
    });

});
