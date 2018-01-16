'use strict';

describe('Controller Tests', function() {

    describe('DragAndDropQuestion Management Detail Controller', function() {
        var $scope, $rootScope;
        var MockEntity, MockPreviousState, MockDragAndDropQuestion, MockDropLocation, MockDragItem, MockDragAndDropMapping;
        var createController;

        beforeEach(inject(function($injector) {
            $rootScope = $injector.get('$rootScope');
            $scope = $rootScope.$new();
            MockEntity = jasmine.createSpy('MockEntity');
            MockPreviousState = jasmine.createSpy('MockPreviousState');
            MockDragAndDropQuestion = jasmine.createSpy('MockDragAndDropQuestion');
            MockDropLocation = jasmine.createSpy('MockDropLocation');
            MockDragItem = jasmine.createSpy('MockDragItem');
            MockDragAndDropMapping = jasmine.createSpy('MockDragAndDropMapping');


            var locals = {
                '$scope': $scope,
                '$rootScope': $rootScope,
                'entity': MockEntity,
                'previousState': MockPreviousState,
                'DragAndDropQuestion': MockDragAndDropQuestion,
                'DropLocation': MockDropLocation,
                'DragItem': MockDragItem,
                'DragAndDropMapping': MockDragAndDropMapping
            };
            createController = function() {
                $injector.get('$controller')("DragAndDropQuestionDetailController", locals);
            };
        }));


        describe('Root Scope Listening', function() {
            it('Unregisters root scope listener upon scope destruction', function() {
                var eventType = 'artemisApp:dragAndDropQuestionUpdate';

                createController();
                expect($rootScope.$$listenerCount[eventType]).toEqual(1);

                $scope.$destroy();
                expect($rootScope.$$listenerCount[eventType]).toBeUndefined();
            });
        });
    });

});
