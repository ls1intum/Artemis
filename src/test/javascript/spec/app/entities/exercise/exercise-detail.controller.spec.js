'use strict';

describe('Controller Tests', function() {

    describe('Exercise Management Detail Controller', function() {
        var $scope, $rootScope;
        var MockEntity, MockExercise, MockCourse, MockParticipation;
        var createController;

        beforeEach(inject(function($injector) {
            $rootScope = $injector.get('$rootScope');
            $scope = $rootScope.$new();
            MockEntity = jasmine.createSpy('MockEntity');
            MockExercise = jasmine.createSpy('MockExercise');
            MockCourse = jasmine.createSpy('MockCourse');
            MockParticipation = jasmine.createSpy('MockParticipation');
            

            var locals = {
                '$scope': $scope,
                '$rootScope': $rootScope,
                'entity': MockEntity ,
                'Exercise': MockExercise,
                'Course': MockCourse,
                'Participation': MockParticipation
            };
            createController = function() {
                $injector.get('$controller')("ExerciseDetailController", locals);
            };
        }));


        describe('Root Scope Listening', function() {
            it('Unregisters root scope listener upon scope destruction', function() {
                var eventType = 'exerciseApplicationApp:exerciseUpdate';

                createController();
                expect($rootScope.$$listenerCount[eventType]).toEqual(1);

                $scope.$destroy();
                expect($rootScope.$$listenerCount[eventType]).toBeUndefined();
            });
        });
    });

});
