'use strict';

describe('Controller Tests', function() {

    describe('DragAndDropAssignment Management Detail Controller', function() {
        var $scope, $rootScope;
        var MockEntity, MockPreviousState, MockDragAndDropAssignment, MockDragItem, MockDropLocation, MockDragAndDropSubmittedAnswer, MockDragAndDropQuestion;
        var createController;

        beforeEach(inject(function($injector) {
            $rootScope = $injector.get('$rootScope');
            $scope = $rootScope.$new();
            MockEntity = jasmine.createSpy('MockEntity');
            MockPreviousState = jasmine.createSpy('MockPreviousState');
            MockDragAndDropAssignment = jasmine.createSpy('MockDragAndDropAssignment');
            MockDragItem = jasmine.createSpy('MockDragItem');
            MockDropLocation = jasmine.createSpy('MockDropLocation');
            MockDragAndDropSubmittedAnswer = jasmine.createSpy('MockDragAndDropSubmittedAnswer');
            MockDragAndDropQuestion = jasmine.createSpy('MockDragAndDropQuestion');


            var locals = {
                '$scope': $scope,
                '$rootScope': $rootScope,
                'entity': MockEntity,
                'previousState': MockPreviousState,
                'DragAndDropAssignment': MockDragAndDropAssignment,
                'DragItem': MockDragItem,
                'DropLocation': MockDropLocation,
                'DragAndDropSubmittedAnswer': MockDragAndDropSubmittedAnswer,
                'DragAndDropQuestion': MockDragAndDropQuestion
            };
            createController = function() {
                $injector.get('$controller')("DragAndDropAssignmentDetailController", locals);
            };
        }));


        describe('Root Scope Listening', function() {
            it('Unregisters root scope listener upon scope destruction', function() {
                var eventType = 'artemisApp:dragAndDropAssignmentUpdate';

                createController();
                expect($rootScope.$$listenerCount[eventType]).toEqual(1);

                $scope.$destroy();
                expect($rootScope.$$listenerCount[eventType]).toBeUndefined();
            });
        });
    });

});
