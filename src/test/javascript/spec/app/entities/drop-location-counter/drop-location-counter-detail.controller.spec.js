'use strict';

describe('Controller Tests', function() {

    describe('DropLocationCounter Management Detail Controller', function() {
        var $scope, $rootScope;
        var MockEntity, MockPreviousState, MockDropLocationCounter, MockDragAndDropStatistic, MockDropLocation;
        var createController;

        beforeEach(inject(function($injector) {
            $rootScope = $injector.get('$rootScope');
            $scope = $rootScope.$new();
            MockEntity = jasmine.createSpy('MockEntity');
            MockPreviousState = jasmine.createSpy('MockPreviousState');
            MockDropLocationCounter = jasmine.createSpy('MockDropLocationCounter');
            MockDragAndDropStatistic = jasmine.createSpy('MockDragAndDropStatistic');
            MockDropLocation = jasmine.createSpy('MockDropLocation');
            

            var locals = {
                '$scope': $scope,
                '$rootScope': $rootScope,
                'entity': MockEntity,
                'previousState': MockPreviousState,
                'DropLocationCounter': MockDropLocationCounter,
                'DragAndDropStatistic': MockDragAndDropStatistic,
                'DropLocation': MockDropLocation
            };
            createController = function() {
                $injector.get('$controller')("DropLocationCounterDetailController", locals);
            };
        }));


        describe('Root Scope Listening', function() {
            it('Unregisters root scope listener upon scope destruction', function() {
                var eventType = 'arTeMiSApp:dropLocationCounterUpdate';

                createController();
                expect($rootScope.$$listenerCount[eventType]).toEqual(1);

                $scope.$destroy();
                expect($rootScope.$$listenerCount[eventType]).toBeUndefined();
            });
        });
    });

});
