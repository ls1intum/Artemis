'use strict';

describe('Controller Tests', function() {

    describe('QuizPointStatistic Management Detail Controller', function() {
        var $scope, $rootScope;
        var MockEntity, MockPreviousState, MockQuizPointStatistic, MockPointCounter, MockQuizExercise;
        var createController;

        beforeEach(inject(function($injector) {
            $rootScope = $injector.get('$rootScope');
            $scope = $rootScope.$new();
            MockEntity = jasmine.createSpy('MockEntity');
            MockPreviousState = jasmine.createSpy('MockPreviousState');
            MockQuizPointStatistic = jasmine.createSpy('MockQuizPointStatistic');
            MockPointCounter = jasmine.createSpy('MockPointCounter');
            MockQuizExercise = jasmine.createSpy('MockQuizExercise');


            var locals = {
                '$scope': $scope,
                '$rootScope': $rootScope,
                'entity': MockEntity,
                'previousState': MockPreviousState,
                'QuizPointStatistic': MockQuizPointStatistic,
                'PointCounter': MockPointCounter,
                'QuizExercise': MockQuizExercise
            };
            createController = function() {
                $injector.get('$controller')("QuizPointStatisticDetailController", locals);
            };
        }));


        describe('Root Scope Listening', function() {
            it('Unregisters root scope listener upon scope destruction', function() {
                var eventType = 'artemisApp:quizPointStatisticUpdate';

                createController();
                expect($rootScope.$$listenerCount[eventType]).toEqual(1);

                $scope.$destroy();
                expect($rootScope.$$listenerCount[eventType]).toBeUndefined();
            });
        });
    });

});
