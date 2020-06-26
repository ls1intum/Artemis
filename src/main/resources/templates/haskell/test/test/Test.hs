module Test where

import qualified Interface as Submission
import qualified Solution

import Test.Tasty
import Test.Tasty.Runners.AntXML

import Test.SmallCheck.Series as SCS
import Test.Tasty.SmallCheck as SC
import Test.Tasty.QuickCheck as QC
import Test.QuickCheck.Assertions
import Test.Tasty.HUnit

import System.Environment (setEnv)

-- SmallChecks

prop_scFibNonNeg :: SCS.NonNegative Integer -> Bool
prop_scFibNonNeg (SCS.NonNegative n) = 0 <= Submission.fib n

scProps :: TestTree
scProps = testGroup "Checked by SmallCheck" [
    SC.testProperty "fib non-negative" prop_scFibNonNeg
  ]

-- QuickChecks

prop_qcSampleSolution :: QC.NonNegative Integer -> Result
prop_qcSampleSolution (QC.NonNegative n) = Submission.fib n ?== Solution.fib n

qcProps :: TestTree
qcProps = testGroup "Checked by QuickCheck" [
    localOption (QC.QuickCheckMaxSize 20) $ QC.testProperty "testing against sample solution" prop_qcSampleSolution
  ]

-- UnitTests

unitTests :: TestTree
unitTests = testGroup "Unit Tests" [
    testCase "fib 1 = 1" $ Submission.fib 1 @?= 1
  ]

-- Final tests wrap up and main

properties :: TestTree
properties = testGroup "Properties" [scProps, qcProps]

tests :: TestTree
tests = testGroup "Tests" [properties, unitTests]

main :: IO ()
main = do
  -- set the default output file path as expected by bamboo
  -- the path can be overwritten by passing --xml=<pathGoesHere>
  setEnv "TASTY_XML" resultsPath
  -- run the tests with xml export
  defaultMainWithIngredients [antXMLRunner] $ (localOption timeoutOption) tests
  where
    resultsPath = "test-reports/results.xml"
    -- by default, run for 1 second
    timeoutOption = mkTimeout (1 * 10^6)

