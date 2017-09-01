'use strict';

describe('Controller Tests', function() {

    describe('Participation Management Detail Controller', function() {
        var $scope, $rootScope;
        var MockEntity, MockPreviousState, MockParticipation, MockResult, MockUser, MockExercise;
        var createController;

        beforeEach(inject(function($injector) {
            $rootScope = $injector.get('$rootScope');
            $scope = $rootScope.$new();
            MockEntity = jasmine.createSpy('MockEntity');
            MockPreviousState = jasmine.createSpy('MockPreviousState');
            MockParticipation = jasmine.createSpy('MockParticipation');
            MockResult = jasmine.createSpy('MockResult');
            MockUser = jasmine.createSpy('MockUser');
            MockExercise = jasmine.createSpy('MockExercise');
            

            var locals = {
                '$scope': $scope,
                '$rootScope': $rootScope,
                'entity': MockEntity,
                'previousState': MockPreviousState,
                'Participation': MockParticipation,
                'Result': MockResult,
                'User': MockUser,
                'Exercise': MockExercise
            };
            createController = function() {
                $injector.get('$controller')("ParticipationDetailController", locals);
            };
        }));


        describe('Root Scope Listening', function() {
            it('Unregisters root scope listener upon scope destruction', function() {
                var eventType = 'artemisApp:participationUpdate';

                createController();
                expect($rootScope.$$listenerCount[eventType]).toEqual(1);

                $scope.$destroy();
                expect($rootScope.$$listenerCount[eventType]).toBeUndefined();
            });
        });
    });

});
