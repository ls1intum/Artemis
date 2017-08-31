'use strict';

describe('Controller Tests', function() {

    describe('DropLocation Management Detail Controller', function() {
        var $scope, $rootScope;
        var MockEntity, MockPreviousState, MockDropLocation, MockDragAndDropQuestion;
        var createController;

        beforeEach(inject(function($injector) {
            $rootScope = $injector.get('$rootScope');
            $scope = $rootScope.$new();
            MockEntity = jasmine.createSpy('MockEntity');
            MockPreviousState = jasmine.createSpy('MockPreviousState');
            MockDropLocation = jasmine.createSpy('MockDropLocation');
            MockDragAndDropQuestion = jasmine.createSpy('MockDragAndDropQuestion');
            

            var locals = {
                '$scope': $scope,
                '$rootScope': $rootScope,
                'entity': MockEntity,
                'previousState': MockPreviousState,
                'DropLocation': MockDropLocation,
                'DragAndDropQuestion': MockDragAndDropQuestion
            };
            createController = function() {
                $injector.get('$controller')("DropLocationDetailController", locals);
            };
        }));


        describe('Root Scope Listening', function() {
            it('Unregisters root scope listener upon scope destruction', function() {
                var eventType = 'exerciseApplicationApp:dropLocationUpdate';

                createController();
                expect($rootScope.$$listenerCount[eventType]).toEqual(1);

                $scope.$destroy();
                expect($rootScope.$$listenerCount[eventType]).toBeUndefined();
            });
        });
    });

});
