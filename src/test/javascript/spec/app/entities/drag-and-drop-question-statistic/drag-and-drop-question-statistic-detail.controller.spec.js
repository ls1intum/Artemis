'use strict';

describe('Controller Tests', function() {

    describe('DragAndDropQuestionStatistic Management Detail Controller', function() {
        var $scope, $rootScope;
        var MockEntity, MockPreviousState, MockDragAndDropQuestionStatistic, MockDropLocationCounter;
        var createController;

        beforeEach(inject(function($injector) {
            $rootScope = $injector.get('$rootScope');
            $scope = $rootScope.$new();
            MockEntity = jasmine.createSpy('MockEntity');
            MockPreviousState = jasmine.createSpy('MockPreviousState');
            MockDragAndDropQuestionStatistic = jasmine.createSpy('MockDragAndDropQuestionStatistic');
            MockDropLocationCounter = jasmine.createSpy('MockDropLocationCounter');
            

            var locals = {
                '$scope': $scope,
                '$rootScope': $rootScope,
                'entity': MockEntity,
                'previousState': MockPreviousState,
                'DragAndDropQuestionStatistic': MockDragAndDropQuestionStatistic,
                'DropLocationCounter': MockDropLocationCounter
            };
            createController = function() {
                $injector.get('$controller')("DragAndDropQuestionStatisticDetailController", locals);
            };
        }));


        describe('Root Scope Listening', function() {
            it('Unregisters root scope listener upon scope destruction', function() {
                var eventType = 'arTeMiSApp:dragAndDropQuestionStatisticUpdate';

                createController();
                expect($rootScope.$$listenerCount[eventType]).toEqual(1);

                $scope.$destroy();
                expect($rootScope.$$listenerCount[eventType]).toBeUndefined();
            });
        });
    });

});
