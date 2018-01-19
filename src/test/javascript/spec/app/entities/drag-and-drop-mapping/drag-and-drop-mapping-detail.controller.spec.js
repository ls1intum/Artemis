'use strict';

describe('Controller Tests', function() {

    describe('DragAndDropMapping Management Detail Controller', function() {
        var $scope, $rootScope;
        var MockEntity, MockPreviousState, MockDragAndDropMapping, MockDragItem, MockDropLocation, MockDragAndDropSubmittedAnswer, MockDragAndDropQuestion;
        var createController;

        beforeEach(inject(function($injector) {
            $rootScope = $injector.get('$rootScope');
            $scope = $rootScope.$new();
            MockEntity = jasmine.createSpy('MockEntity');
            MockPreviousState = jasmine.createSpy('MockPreviousState');
            MockDragAndDropMapping = jasmine.createSpy('MockDragAndDropMapping');
            MockDragItem = jasmine.createSpy('MockDragItem');
            MockDropLocation = jasmine.createSpy('MockDropLocation');
            MockDragAndDropSubmittedAnswer = jasmine.createSpy('MockDragAndDropSubmittedAnswer');
            MockDragAndDropQuestion = jasmine.createSpy('MockDragAndDropQuestion');


            var locals = {
                '$scope': $scope,
                '$rootScope': $rootScope,
                'entity': MockEntity,
                'previousState': MockPreviousState,
                'DragAndDropMapping': MockDragAndDropMapping,
                'DragItem': MockDragItem,
                'DropLocation': MockDropLocation,
                'DragAndDropSubmittedAnswer': MockDragAndDropSubmittedAnswer,
                'DragAndDropQuestion': MockDragAndDropQuestion
            };
            createController = function() {
                $injector.get('$controller')("DragAndDropMappingDetailController", locals);
            };
        }));


        describe('Root Scope Listening', function() {
            it('Unregisters root scope listener upon scope destruction', function() {
                var eventType = 'artemisApp:dragAndDropMappingUpdate';

                createController();
                expect($rootScope.$$listenerCount[eventType]).toEqual(1);

                $scope.$destroy();
                expect($rootScope.$$listenerCount[eventType]).toBeUndefined();
            });
        });
    });

});
