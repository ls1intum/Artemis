classdef GradeTest < matlab.unittest.TestCase
    properties
        grades
    end

    methods (TestClassSetup)
        % Shared setup for the entire test class
    end

    methods (TestMethodSetup)
        % Setup for each test

        function setupGrades(testCase)
            testCase.grades = [1.3 3.3 4.0 4.7
                               2.7 1.7 4.0 1.7
                               1.7 3.7 3.0 4.3
                               4.3 2.3 1.7 3.3
                               2.3 3.7 2.0 5.0];
        end
    end

    methods (Test)
        % Test methods

        function testMedianGradeByAssignment(testCase)
            actual = medianGradeByAssignment(testCase.grades);
            expected = [2.3 3.3 3.0 4.3];
            testCase.assertEqual(actual,expected,"median is incorrect",AbsTol=0.0001);
        end

        function testAverageGradeByStudent(testCase)
            actual = averageGradeByStudent(testCase.grades);
            expected = [3.325 2.525 3.175 2.9 3.25];
            testCase.assertEqual(actual,expected,"average is incorrect",AbsTol=0.0001);
        end

        function testFinalGrade(testCase)
            weights = [0.1 0.1 0.5 0.3];
            actual = finalGrade(testCase.grades,weights);
            expected = [3.9 3.0 3.3 2.5 3.1];
            testCase.assertEqual(actual,expected,"final grades are incorrect",AbsTol=0.0001);
        end
    end

end
