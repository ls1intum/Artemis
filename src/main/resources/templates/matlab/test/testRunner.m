import matlab.unittest.plugins.XMLPlugin

addpath("../${studentParentWorkingDirectoryName}")

runner = testrunner;

plugin = XMLPlugin.producingJUnitFormat("test-results/results.xml");
addPlugin(runner,plugin);

suite = testsuite("tests");

run(runner,suite);
