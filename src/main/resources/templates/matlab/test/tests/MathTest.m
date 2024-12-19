classdef MathTest < matlab.unittest.TestCase

    methods (TestClassSetup)
        % Shared setup for the entire test class
    end

    methods (TestMethodSetup)
        % Setup for each test
    end

    methods (Test)
        % Test methods

        function testSquare(testCase)
            actual = square(3);
            expected = 9;
            testCase.assertEqual(actual,expected,"square(3) should be 9")
        end
    end

end