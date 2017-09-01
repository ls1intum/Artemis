'use strict';

describe('Controller Tests', function() {

    describe('DragItem Management Detail Controller', function() {
        var $scope, $rootScope;
        var MockEntity, MockPreviousState, MockDragItem, MockDropLocation, MockDragAndDropQuestion;
        var createController;

        beforeEach(inject(function($injector) {
            $rootScope = $injector.get('$rootScope');
            $scope = $rootScope.$new();
            MockEntity = jasmine.createSpy('MockEntity');
            MockPreviousState = jasmine.createSpy('MockPreviousState');
            MockDragItem = jasmine.createSpy('MockDragItem');
            MockDropLocation = jasmine.createSpy('MockDropLocation');
            MockDragAndDropQuestion = jasmine.createSpy('MockDragAndDropQuestion');
            

            var locals = {
                '$scope': $scope,
                '$rootScope': $rootScope,
                'entity': MockEntity,
                'previousState': MockPreviousState,
                'DragItem': MockDragItem,
                'DropLocation': MockDropLocation,
                'DragAndDropQuestion': MockDragAndDropQuestion
            };
            createController = function() {
                $injector.get('$controller')("DragItemDetailController", locals);
            };
        }));


        describe('Root Scope Listening', function() {
            it('Unregisters root scope listener upon scope destruction', function() {
                var eventType = 'artemisApp:dragItemUpdate';

                createController();
                expect($rootScope.$$listenerCount[eventType]).toEqual(1);

                $scope.$destroy();
                expect($rootScope.$$listenerCount[eventType]).toBeUndefined();
            });
        });
    });

});
