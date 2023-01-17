package simulations

import _root_.io.gatling.core.scenario.Simulation
import io.gatling.core.Predef._
import io.gatling.http.Predef._

class GetCoursesSimulation extends Simulation {
    val username = "<YOUR USERNAME>"    // NOTE: replace with actual username
    val password = "<YOUR PASSWORD>"    // NOTE: replace with actual password

    val baseURL = Option(System.getProperty("baseURL")) getOrElse """http://localhost:8080"""

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
        "X-XSRF-TOKEN" -> "${xsrf_token}"
    )

    val scn = scenario("Test Getting all Course entities, creating and deleting a course")
        .exec(http("First unauthenticated request")
            .get("/api/public/account")
            .headers(headers_http)
            .check(status.is(401))
            .check(headerRegex("Set-Cookie", "XSRF-TOKEN=(.*);[\\s]").saveAs("xsrf_token"))).exitHereIfFailed
        .pause(2)
        .exec(http("Authentication")
            .post("/api/authentication")
            .headers(headers_http_authenticated)
            .formParam("j_username", username)
            .formParam("j_password", password)
            .formParam("remember-me", "true")
            .formParam("submit", "Login")
            .check(headerRegex("Set-Cookie", "XSRF-TOKEN=(.*);[\\s]").saveAs("xsrf_token"))).exitHereIfFailed
        .pause(1)
        .exec(http("Authenticated request")
            .get("/api/public/account")
            .headers(headers_http_authenticated)
            .check(status.is(200)))
        .pause(2)
        .repeat(2) {
            exec(http("Get all courses")
                .get("/api/courses")
                .headers(headers_http_authenticated)
                .check(status.is(200)))
                .pause(2, 5)
                .exec(http("Create new course")
                    .post("/api/courses")
                    .headers(headers_http_authenticated)
                    .body(StringBody("""{"id":null, "title":"SAMPLE_TEXT", "studentGroupName":"SAMPLE_TEXT", "teachingAssistantGroupName":"SAMPLE_TEXT"}""")).asJson
                    .check(status.is(201))
                    .check(headerRegex("Location", "(.*)").saveAs("new_course_url"))).exitHereIfFailed
                .pause(2)
                .repeat(5) {
                    exec(http("Get created course")
                        .get("${new_course_url}")
                        .headers(headers_http_authenticated))
                        .pause(2)
                }
                .exec(http("Delete created course")
                    .delete("${new_course_url}")
                    .headers(headers_http_authenticated))
                .pause(2)
        }

    val users = scenario("Users").exec(scn)

    setUp(
        users.inject(rampUsers(Integer.getInteger("users", 500)) during(Integer.getInteger("ramp", 30) seconds))
    ).protocols(httpConf)

}
