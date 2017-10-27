'use strict';

describe('Controller Tests', function() {

    describe('MultipleChoiceStatistic Management Detail Controller', function() {
        var $scope, $rootScope;
        var MockEntity, MockPreviousState, MockMultipleChoiceStatistic, MockAnswerCounter;
        var createController;

        beforeEach(inject(function($injector) {
            $rootScope = $injector.get('$rootScope');
            $scope = $rootScope.$new();
            MockEntity = jasmine.createSpy('MockEntity');
            MockPreviousState = jasmine.createSpy('MockPreviousState');
            MockMultipleChoiceStatistic = jasmine.createSpy('MockMultipleChoiceStatistic');
            MockAnswerCounter = jasmine.createSpy('MockAnswerCounter');
            

            var locals = {
                '$scope': $scope,
                '$rootScope': $rootScope,
                'entity': MockEntity,
                'previousState': MockPreviousState,
                'MultipleChoiceStatistic': MockMultipleChoiceStatistic,
                'AnswerCounter': MockAnswerCounter
            };
            createController = function() {
                $injector.get('$controller')("MultipleChoiceStatisticDetailController", locals);
            };
        }));


        describe('Root Scope Listening', function() {
            it('Unregisters root scope listener upon scope destruction', function() {
                var eventType = 'arTeMiSApp:multipleChoiceStatisticUpdate';

                createController();
                expect($rootScope.$$listenerCount[eventType]).toEqual(1);

                $scope.$destroy();
                expect($rootScope.$$listenerCount[eventType]).toBeUndefined();
            });
        });
    });

});
