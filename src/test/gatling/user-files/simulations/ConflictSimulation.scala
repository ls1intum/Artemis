package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class ConflictSimulation extends Simulation {

    val httpProtocol = http
        .baseUrl("http://localhost:9000")
        .inferHtmlResources()
        .acceptHeader("*/*")
        .acceptEncodingHeader("gzip, deflate")
        .acceptLanguageHeader("en-US,en;q=0.5")
        .userAgentHeader("Mozilla/5.0 (X11; Fedora; Linux x86_64; rv:66.0) Gecko/20100101 Firefox/66.0")
        .silentResources

    val userCredentials: Array[(String, String)] = Array(("artemis_test_user_1", "ArTEMiS_1_pw2017"), ("artemis_test_user_2", "ArTEMiS_2_pw2017"))


    val headers_http = Map(
        "Accept" -> """application/json"""
    )

    val headers_http_authentication = Map(
        "Accept" -> """application/json""",
        "Content-Type" -> """application/json""",
        "X-XSRF-TOKEN" -> "${xsrf_token}"
    )

    val headers_http_authenticated = Map(
        "Accept" -> """application/json""",
        "X-XSRF-TOKEN" -> "${xsrf_token}",
        "Authorization" -> "${access_token}"
    )

    val headers_http_authenticated_JSON = Map(
        "Accept" -> """application/json""",
        "Content-Type" -> """application/json""",
        "X-XSRF-TOKEN" -> "${xsrf_token}",
        "Authorization" -> "${access_token}"
    )

    val scn = scenario("Test the ModelingSubmission entity")
        .exec(http("First unauthenticated request")
            .get("/api/account")
            .headers(headers_http)
            .check(status.is(401))
            .check(headerRegex("set-cookie", "XSRF-TOKEN=(.*);[\\s]").saveAs("xsrf_token"))).exitHereIfFailed
        .exec(http("Authentication")
            .post("/api/authenticate")
            .headers(headers_http_authentication)
            .body(StringBody("""{"username":"""" + userCredentials(0)._1 + """", "password":"""" + userCredentials(0)._2 + """"}""")).asJson
            .check(status.is(200))
            .check(header("Authorization").saveAs("access_token"))).exitHereIfFailed
        .exec((http("Create Course"))
            .post("/api/courses")
            .headers(headers_http_authenticated_JSON)
            .body(StringBody("""{"id":null,"title":"CourseXY","shortName":"TTTXY","studentGroupName":"tumuser","teachingAssistantGroupName":null,"instructorGroupName":"tumuser","description":null,"startDate":null,"endDate":null,"onlineCourse":false,"registrationEnabled":false,"color":null,"courseIcon":null}""")).asJson
            .check(status.is(201))
            .check(jsonPath("$.id").saveAs("course_id"))
            .check(headerRegex("set-cookie", "XSRF-TOKEN=(.*);[\\s]").saveAs("xsrf_token"))).exitHereIfFailed
        .exec((http("Create ModelingExercise"))
            .post("/api/modeling-exercises")
            .headers(headers_http_authenticated_JSON)
            .body(StringBody("""{"isAtLeastTutor":false,"isAtLeastInstructor":false,"type":"modeling","automaticAssessmentSupported":false,"course":{"id":""" + "${course_id}" + ""","title":"CourseXY","shortName":"TTTXY","studentGroupName":"tumuser","instructorGroupName":"tumuser","onlineCourse":false,"registrationEnabled":false,"startDate":null,"endDate":null,"exercises":[]},"diagramType":"ClassDiagram","title":"Exercise 1","maxScore":10,"problemStatement":"","releaseDate":null,"dueDate":null,"assessmentDueDate":null}""")).asJson
            .check(status.is(201))
            .check(jsonPath("$.id").saveAs("exercise_id"))
            .check(headerRegex("set-cookie", "XSRF-TOKEN=(.*);[\\s]").saveAs("xsrf_token"))).exitHereIfFailed
        .exec((http("Participate in Modeling Exercise"))
            .post("/api/courses/${course_id}/exercises/${exercise_id}/participations")
            .headers(headers_http_authenticated_JSON)
            .check(status.is(201))
            .check(jsonPath("$.id").saveAs("participation_id"))
            .check(bodyString.saveAs("participation"))
            .check(headerRegex("set-cookie", "XSRF-TOKEN=(.*);[\\s]").saveAs("xsrf_token"))).exitHereIfFailed
        .exec((http("Submit Model"))
            .put("/api/exercises/${exercise_id}/modeling-submissions")
            .headers(headers_http_authenticated_JSON)
            .body(StringBody("""{"submissionExerciseType":"modeling","submitted":false,"participation":"${participation}","model":""}""")).asJson
            .check(status.is(200)))

    setUp(scn.inject(atOnceUsers(1))).protocols(httpProtocol)
}
