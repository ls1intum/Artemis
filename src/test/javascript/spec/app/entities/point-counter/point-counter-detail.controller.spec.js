'use strict';

describe('Controller Tests', function() {

    describe('PointCounter Management Detail Controller', function() {
        var $scope, $rootScope;
        var MockEntity, MockPreviousState, MockPointCounter, MockQuizPointStatistic;
        var createController;

        beforeEach(inject(function($injector) {
            $rootScope = $injector.get('$rootScope');
            $scope = $rootScope.$new();
            MockEntity = jasmine.createSpy('MockEntity');
            MockPreviousState = jasmine.createSpy('MockPreviousState');
            MockPointCounter = jasmine.createSpy('MockPointCounter');
            MockQuizPointStatistic = jasmine.createSpy('MockQuizPointStatistic');


            var locals = {
                '$scope': $scope,
                '$rootScope': $rootScope,
                'entity': MockEntity,
                'previousState': MockPreviousState,
                'PointCounter': MockPointCounter,
                'QuizPointStatistic': MockQuizPointStatistic
            };
            createController = function() {
                $injector.get('$controller')("PointCounterDetailController", locals);
            };
        }));


        describe('Root Scope Listening', function() {
            it('Unregisters root scope listener upon scope destruction', function() {
                var eventType = 'artemisApp:pointCounterUpdate';

                createController();
                expect($rootScope.$$listenerCount[eventType]).toEqual(1);

                $scope.$destroy();
                expect($rootScope.$$listenerCount[eventType]).toBeUndefined();
            });
        });
    });

});
