package de.tum.in.www1.exerciseapp.cucumber;

import org.junit.runner.RunWith;


import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles(profiles = "jira,bamboo,bitbucket")
@RunWith(Cucumber.class)
@CucumberOptions(plugin = "pretty", features = "src/test/features")
public class CucumberTest  {

}
