package simulations

import _root_.io.gatling.core.scenario.Simulation
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class QuizParticipationSimulation extends Simulation {

    // TODO replace exerciseID with id of dynamically created exercise
    val exerciseId = 113

    val feeder = Iterator.tabulate(500)(i => Map(
        "username" -> ("<USERNAME>"),   // TODO: generate real username for each value of i (removed for security)
        "password" -> ("<PASSWORD>")    // TODO: generate real password for each value of i (removed for security)
    ))

    val baseURL = "http://localhost:8080"

    val httpConf = http
        .baseURL(baseURL)
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

    val login = exec(
        http("First unauthenticated request")
            .get("/api/account")
            .headers(headers_http)
            .check(status.is(401))
            .check(headerRegex("Set-Cookie", "XSRF-TOKEN=(.*);[\\s]").saveAs("xsrf_token"))).exitHereIfFailed
        .feed(feeder)
        .pause(2 seconds)
        .exec(http("Authentication")
            .post("/api/authentication")
            .headers(headers_http_authenticated)
            .formParam("j_username", "${username}")
            .formParam("j_password", "${password}")
            .formParam("remember-me", "true")
            .formParam("submit", "Login")
            .check(status.is(200))
            .check(headerRegex("Set-Cookie", "XSRF-TOKEN=(.*);[\\s]").saveAs("xsrf_token"))).exitHereIfFailed
        .pause(2 seconds)
        .exec(http("Authenticated request")
            .get("/api/account")
            .headers(headers_http_authenticated)
            .check(status.is(200)))

    val startQuiz = exec(
        http("Get quiz")
            .get("/api/courses/10/exercises/113/submissions/my-latest")
            .headers(headers_http_authenticated)
            .check(status.is(200))
            .check(bodyString.saveAs("quizExercise"))).exitHereIfFailed
        .exec(
            http("Start Quiz")
                .get("/api/courses/1/exercises/" + exerciseId + "/submissions/my-latest")
                .headers(headers_http_authenticated)
                .check(status.is(200))
                .check(regex("\"id\" : (\\d*),").saveAs("submissionID"))).exitHereIfFailed
        .pause(2 seconds, 5 seconds)

    // TODO: add websocket and submit

    val studentScenario = scenario("Test Quiz Participation").exec(login, startQuiz)

    val users = scenario("Users").exec(studentScenario)

    setUp(
        users.inject(rampUsers(500) over (30 seconds))
    ).protocols(httpConf)

}
