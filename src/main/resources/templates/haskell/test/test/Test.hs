{-# LANGUAGE CPP #-}
module Test where

import qualified Interface as Sub
import qualified Solution as Sol

import Test.Tasty
import Test.Tasty.Runners.AntXML 
import Test.SmallCheck.Series as SCS
import Test.Tasty.SmallCheck as SC
import Test.Tasty.QuickCheck as QC
import Test.QuickCheck.Assertions
import Test.Tasty.HUnit

import System.Environment (setEnv)

-- SmallChecks

prop_scFilter :: ((Int,Int) -> [(Int,Int)] -> [(Int,Int)]) -> ((Int,Int) -> [(Int,Int)] -> [(Int,Int)]) -> (Int,Int) -> [(Int,Int)] -> Bool
prop_scFilter sub sol interval xs = map fst (sub interval xs) == map fst (sol interval xs)

prop_scMap :: ((Int,Int) -> [(Int,Int)] -> [(Int,Int)]) -> ((Int,Int) -> [(Int,Int)] -> [(Int,Int)]) -> [(Int,Int)] -> Bool
prop_scMap sub sol xs = map snd (sub (minBound, maxBound) xs) == map snd (sol (minBound, maxBound) xs)

scProps :: TestTree
scProps = localOption (SC.SmallCheckDepth 3) $ testGroup "Checked by SmallCheck" [
    SC.testProperty "Testing filtering in A" $ prop_scFilter Sub.selectAndReflectA Sol.selectAndReflectA,
    SC.testProperty "Testing mapping in A" $ prop_scMap Sub.selectAndReflectA Sol.selectAndReflectA,
    SC.testProperty "Testing filtering in B" $ prop_scFilter Sub.selectAndReflectB Sol.selectAndReflectB,
    SC.testProperty "Testing mapping in B" $ prop_scMap Sub.selectAndReflectB Sol.selectAndReflectB,
    SC.testProperty "Testing filtering in C" $ prop_scFilter Sub.selectAndReflectC Sol.selectAndReflectC,
    SC.testProperty "Testing mapping in C" $ prop_scMap Sub.selectAndReflectC Sol.selectAndReflectC
  ]

-- QuickChecks

prop_qcSampleSolution :: ((Int,Int) -> [(Int,Int)] -> [(Int,Int)]) -> ((Int,Int) -> [(Int,Int)] -> [(Int,Int)]) -> (Int,Int) -> [(Int,Int)] -> Result
prop_qcSampleSolution sub sol interval xs = sub interval xs ?== sol interval xs

qcProps :: TestTree
qcProps = testGroup "Checked by QuickCheck" [
    QC.testProperty "Testing A against sample solution" $ prop_qcSampleSolution Sub.selectAndReflectA Sol.selectAndReflectA,
    QC.testProperty "Testing B against sample solution" $ prop_qcSampleSolution Sub.selectAndReflectB Sol.selectAndReflectB,
    QC.testProperty "Testing C against sample solution" $ prop_qcSampleSolution Sub.selectAndReflectC Sol.selectAndReflectC
  ]

-- UnitTests

unitTests :: TestTree
unitTests = testGroup "Unit Tests" [
    testCase "Testing selectAndReflectA (0,0) []" $ Sub.selectAndReflectA (0,0) [] @?= Sol.selectAndReflectA (0,0) [],
    testCase "Testing selectAndReflectB (0,1) [(0,0)]" $ Sub.selectAndReflectB (0,1) [(0,0)] @?= Sol.selectAndReflectB (0,1) [(0,0)],
    testCase "Testing selectAndReflectC (0,1) [(-1,-1)]" $ Sub.selectAndReflectC (0,1) [(-1,-1)] @?= Sol.selectAndReflectC (0,1) [(-1,-1)]
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
  testRunner $ localOption timeoutOption tests
  where
    resultsPath = "test-reports/results.xml"
#ifdef PROD 
    -- on the server (production mode), run tests with xml output
    testRunner = defaultMainWithIngredients [antXMLRunner]
#else
    -- locally, run tests with terminal output
    testRunner = defaultMain
#endif    
    -- by default, run for 1 second
    timeoutOption = mkTimeout (1 * 10^6)
