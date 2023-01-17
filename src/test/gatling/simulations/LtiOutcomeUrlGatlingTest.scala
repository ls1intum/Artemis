package simulations

import _root_.io.gatling.core.scenario.Simulation
import ch.qos.logback.classic.LoggerContext
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

/**
 * Performance test for the LtiOutcomeUrl entity.
 */
class LtiOutcomeUrlGatlingTest extends Simulation {

    val context: LoggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    // Log all HTTP requests
    //context.getLogger("io.gatling.http").setLevel(Level.valueOf("TRACE"))
    // Log failed HTTP requests
    //context.getLogger("io.gatling.http").setLevel(Level.valueOf("DEBUG"))

    val baseURL = Option(System.getProperty("baseURL")) getOrElse """http://127.0.0.1:8080"""

    val httpConf = http
        .baseUrl(baseURL)
        .inferHtmlResources()
        .acceptHeader("*/*")
        .acceptEncodingHeader("gzip, deflate")
        .acceptLanguageHeader("fr,fr-fr;q=0.8,en-us;q=0.5,en;q=0.3")
        .connectionHeader("keep-alive")
        .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.10; rv:33.0) Gecko/20100101 Firefox/33.0")

    val headers_http = Map(
        "Accept" -> """application/json"""
    )

    val headers_http_authenticated = Map(
        "Accept" -> """application/json""",
    )

    val scn = scenario("Test the LtiOutcomeUrl entity")
        .exec(http("First unauthenticated request")
        .get("/api/public/account")
        .headers(headers_http)
        .check(status.is(401))
        .pause(10)
        .exec(http("Authentication")
        .post("/api/authentication")
        .headers(headers_http_authenticated)
        .formParam("j_username", "admin")
        .formParam("j_password", "admin")
        .formParam("remember-me", "true")
        .formParam("submit", "Login")).exitHereIfFailed
        .pause(1)
        .exec(http("Authenticated request")
        .get("/api/public/account")
        .headers(headers_http_authenticated)
        .check(status.is(200))
        .pause(10)
        .repeat(2) {
            exec(http("Get all ltiOutcomeUrls")
            .get("/api/lti-outcome-urls")
            .headers(headers_http_authenticated)
            .check(status.is(200)))
            .pause(10 seconds, 20 seconds)
            .exec(http("Create new ltiOutcomeUrl")
            .post("/api/lti-outcome-urls")
            .headers(headers_http_authenticated)
            .body(StringBody("""{"id":null, "url":"SAMPLE_TEXT", "sourcedId":"SAMPLE_TEXT"}""")).asJson
            .check(status.is(201))
            .check(headerRegex("Location", "(.*)").saveAs("new_ltiOutcomeUrl_url"))).exitHereIfFailed
            .pause(10)
            .repeat(5) {
                exec(http("Get created ltiOutcomeUrl")
                .get("${new_ltiOutcomeUrl_url}")
                .headers(headers_http_authenticated))
                .pause(10)
            }
            .exec(http("Delete created ltiOutcomeUrl")
            .delete("${new_ltiOutcomeUrl_url}")
            .headers(headers_http_authenticated))
            .pause(10)
        }

    val users = scenario("Users").exec(scn)

    setUp(
        users.inject(rampUsers(100) during(1 minutes))
    ).protocols(httpConf)
}
