'use strict';

describe('Controller Tests', function() {

    describe('DragAndDropStatistic Management Detail Controller', function() {
        var $scope, $rootScope;
        var MockEntity, MockPreviousState, MockDragAndDropStatistic, MockDropLocationCounter;
        var createController;

        beforeEach(inject(function($injector) {
            $rootScope = $injector.get('$rootScope');
            $scope = $rootScope.$new();
            MockEntity = jasmine.createSpy('MockEntity');
            MockPreviousState = jasmine.createSpy('MockPreviousState');
            MockDragAndDropStatistic = jasmine.createSpy('MockDragAndDropStatistic');
            MockDropLocationCounter = jasmine.createSpy('MockDropLocationCounter');
            

            var locals = {
                '$scope': $scope,
                '$rootScope': $rootScope,
                'entity': MockEntity,
                'previousState': MockPreviousState,
                'DragAndDropStatistic': MockDragAndDropStatistic,
                'DropLocationCounter': MockDropLocationCounter
            };
            createController = function() {
                $injector.get('$controller')("DragAndDropStatisticDetailController", locals);
            };
        }));


        describe('Root Scope Listening', function() {
            it('Unregisters root scope listener upon scope destruction', function() {
                var eventType = 'arTeMiSApp:dragAndDropStatisticUpdate';

                createController();
                expect($rootScope.$$listenerCount[eventType]).toEqual(1);

                $scope.$destroy();
                expect($rootScope.$$listenerCount[eventType]).toBeUndefined();
            });
        });
    });

});
