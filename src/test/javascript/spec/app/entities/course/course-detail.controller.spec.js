'use strict';

describe('Controller Tests', function() {

    describe('Course Management Detail Controller', function() {
        var $scope, $rootScope;
        var MockEntity, MockCourse, MockExercise;
        var createController;

        beforeEach(inject(function($injector) {
            $rootScope = $injector.get('$rootScope');
            $scope = $rootScope.$new();
            MockEntity = jasmine.createSpy('MockEntity');
            MockCourse = jasmine.createSpy('MockCourse');
            MockExercise = jasmine.createSpy('MockExercise');
            

            var locals = {
                '$scope': $scope,
                '$rootScope': $rootScope,
                'entity': MockEntity ,
                'Course': MockCourse,
                'Exercise': MockExercise
            };
            createController = function() {
                $injector.get('$controller')("CourseDetailController", locals);
            };
        }));


        describe('Root Scope Listening', function() {
            it('Unregisters root scope listener upon scope destruction', function() {
                var eventType = 'exerciseApplicationApp:courseUpdate';

                createController();
                expect($rootScope.$$listenerCount[eventType]).toEqual(1);

                $scope.$destroy();
                expect($rootScope.$$listenerCount[eventType]).toBeUndefined();
            });
        });
    });

});
