'use strict';

describe('Controller Tests', function() {

    describe('Result Management Detail Controller', function() {
        var $scope, $rootScope;
        var MockEntity, MockPreviousState, MockResult, MockParticipation;
        var createController;

        beforeEach(inject(function($injector) {
            $rootScope = $injector.get('$rootScope');
            $scope = $rootScope.$new();
            MockEntity = jasmine.createSpy('MockEntity');
            MockPreviousState = jasmine.createSpy('MockPreviousState');
            MockResult = jasmine.createSpy('MockResult');
            MockParticipation = jasmine.createSpy('MockParticipation');
            

            var locals = {
                '$scope': $scope,
                '$rootScope': $rootScope,
                'entity': MockEntity,
                'previousState': MockPreviousState,
                'Result': MockResult,
                'Participation': MockParticipation
            };
            createController = function() {
                $injector.get('$controller')("ResultDetailController", locals);
            };
        }));


        describe('Root Scope Listening', function() {
            it('Unregisters root scope listener upon scope destruction', function() {
                var eventType = 'exerciseApplicationApp:resultUpdate';

                createController();
                expect($rootScope.$$listenerCount[eventType]).toEqual(1);

                $scope.$destroy();
                expect($rootScope.$$listenerCount[eventType]).toBeUndefined();
            });
        });
    });

});
