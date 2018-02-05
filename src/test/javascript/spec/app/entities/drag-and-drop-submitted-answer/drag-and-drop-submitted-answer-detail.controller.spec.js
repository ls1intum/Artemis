'use strict';

describe('Controller Tests', function() {

    describe('DragAndDropSubmittedAnswer Management Detail Controller', function() {
        var $scope, $rootScope;
        var MockEntity, MockPreviousState, MockDragAndDropSubmittedAnswer, MockDragAndDropMapping;
        var createController;

        beforeEach(inject(function($injector) {
            $rootScope = $injector.get('$rootScope');
            $scope = $rootScope.$new();
            MockEntity = jasmine.createSpy('MockEntity');
            MockPreviousState = jasmine.createSpy('MockPreviousState');
            MockDragAndDropSubmittedAnswer = jasmine.createSpy('MockDragAndDropSubmittedAnswer');
            MockDragAndDropMapping = jasmine.createSpy('MockDragAndDropMapping');


            var locals = {
                '$scope': $scope,
                '$rootScope': $rootScope,
                'entity': MockEntity,
                'previousState': MockPreviousState,
                'DragAndDropSubmittedAnswer': MockDragAndDropSubmittedAnswer,
                'DragAndDropMapping': MockDragAndDropMapping
            };
            createController = function() {
                $injector.get('$controller')("DragAndDropSubmittedAnswerDetailController", locals);
            };
        }));


        describe('Root Scope Listening', function() {
            it('Unregisters root scope listener upon scope destruction', function() {
                var eventType = 'artemisApp:dragAndDropSubmittedAnswerUpdate';

                createController();
                expect($rootScope.$$listenerCount[eventType]).toEqual(1);

                $scope.$destroy();
                expect($rootScope.$$listenerCount[eventType]).toBeUndefined();
            });
        });
    });

});
