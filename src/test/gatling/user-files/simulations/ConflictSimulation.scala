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
    val model1: String = """{\"version\":\"2.0.0\",\"size\":{\"width\":910,\"height\":370},\"type\":\"ClassDiagram\",\"interactive\":{\"elements\":[],\"relationships\":[]},\"elements\":[{\"id\":\"62c4675c-dd53-4e5c-bc18-0042081b9ac7\",\"name\":\"Tea\",\"owner\":null,\"type\":\"Class\",\"bounds\":{\"x\":0,\"y\":270,\"width\":200,\"height\":100},\"attributes\":[\"e0133088-a2d2-4913-bfab-550182fbf77d\"],\"methods\":[\"dfe1c9e9-d4aa-4707-aedd-19b0406c2e20\"]},{\"id\":\"e0133088-a2d2-4913-bfab-550182fbf77d\",\"name\":\"+ temp: Type\",\"owner\":\"62c4675c-dd53-4e5c-bc18-0042081b9ac7\",\"type\":\"ClassAttribute\",\"bounds\":{\"x\":0,\"y\":310,\"width\":200,\"height\":30}},{\"id\":\"dfe1c9e9-d4aa-4707-aedd-19b0406c2e20\",\"name\":\"+ drink()\",\"owner\":\"62c4675c-dd53-4e5c-bc18-0042081b9ac7\",\"type\":\"ClassMethod\",\"bounds\":{\"x\":0,\"y\":340,\"width\":200,\"height\":30}},{\"id\":\"2ac69a3b-7e75-4010-8af5-0785fe3aaa4d\",\"name\":\"Class\",\"owner\":null,\"type\":\"Class\",\"bounds\":{\"x\":710,\"y\":270,\"width\":200,\"height\":100},\"attributes\":[\"b94f0549-cfcc-4069-9058-f66413b18cde\"],\"methods\":[\"5181da8e-6f29-4b46-97c3-2eca7e1ee1e3\"]},{\"id\":\"b94f0549-cfcc-4069-9058-f66413b18cde\",\"name\":\"+ attribute: Type\",\"owner\":\"2ac69a3b-7e75-4010-8af5-0785fe3aaa4d\",\"type\":\"ClassAttribute\",\"bounds\":{\"x\":710,\"y\":310,\"width\":200,\"height\":30}},{\"id\":\"5181da8e-6f29-4b46-97c3-2eca7e1ee1e3\",\"name\":\"+ method()\",\"owner\":\"2ac69a3b-7e75-4010-8af5-0785fe3aaa4d\",\"type\":\"ClassMethod\",\"bounds\":{\"x\":710,\"y\":340,\"width\":200,\"height\":30}},{\"id\":\"c430c7a2-046f-45d7-925f-a754c7a3190f\",\"name\":\"Drink\",\"owner\":null,\"type\":\"AbstractClass\",\"bounds\":{\"x\":360,\"y\":0,\"width\":200,\"height\":70},\"attributes\":[],\"methods\":[\"6819b6c6-12ed-4f07-98a4-94d76d7409bc\"]},{\"id\":\"6819b6c6-12ed-4f07-98a4-94d76d7409bc\",\"name\":\"+ drink()\",\"owner\":\"c430c7a2-046f-45d7-925f-a754c7a3190f\",\"type\":\"ClassMethod\",\"bounds\":{\"x\":360,\"y\":40,\"width\":200,\"height\":30}}],\"relationships\":[],\"assessments\":[]}"""


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

    val firstModel = scenario("Submit 1. Model")
        .exec(http("First unauthenticated request")
            .get("/api/account")
            .headers(headers_http)
            .check(status.is(401))
            .check(headerRegex("set-cookie", "XSRF-TOKEN=(.*);[\\s]").saveAs("xsrf_token"))).exitHereIfFailed
        .exec(http("Authentication of 1. User")
            .post("/api/authenticate")
            .headers(headers_http_authentication)
            .body(StringBody("""{"username":"""" + userCredentials(0)._1 + """", "password":"""" + userCredentials(0)._2 + """"}""")).asJson
            .check(status.is(200))
            .check(header("Authorization").saveAs("access_token"))).exitHereIfFailed
        .exec((http("Create Course"))
            .post("/api/courses")
            .headers(headers_http_authenticated_JSON)
            .body(StringBody("""{"id":null,"title":"GatlingGeneratedCourse","shortName":"TTTXY","studentGroupName":"tumuser","teachingAssistantGroupName":null,"instructorGroupName":"tumuser","description":null,"startDate":null,"endDate":null,"onlineCourse":false,"registrationEnabled":false,"color":null,"courseIcon":null}""")).asJson
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
        .exec((http("Submit Model1"))
            .put("/api/exercises/${exercise_id}/modeling-submissions")
            .headers(headers_http_authenticated_JSON)
            .body(StringBody("""{"submissionExerciseType":"modeling","submitted":true,"model":"""" + model1 + """"}""")).asJson
            .check(status.is(200)))

    val secondModel = scenario("Submit 2nd. Model")
        .exec(flushSessionCookies)
        .exec(http("First unauthenticated request")
            .get("/api/account")
            .headers(headers_http)
            .check(status.is(401))
            .check(headerRegex("set-cookie", "XSRF-TOKEN=(.*);[\\s]").saveAs("xsrf_token"))).exitHereIfFailed
        .exec(http("Authentication of 2nd User")
            .post("/api/authenticate")
            .headers(headers_http_authentication)
            .body(StringBody("""{"username":"""" + userCredentials(1)._1 + """", "password":"""" + userCredentials(1)._2 + """"}""")).asJson
            .check(status.is(200))
            .check(header("Authorization").saveAs("access_token"))).exitHereIfFailed
        .exec((http("Participate in Modeling Exercise"))
            .post("/api/courses/${course_id}/exercises/${exercise_id}/participations")
            .headers(headers_http_authenticated_JSON)
            .check(status.is(201))
            .check(jsonPath("$.id").saveAs("participation_id"))
            .check(bodyString.saveAs("participation"))
            .check(headerRegex("set-cookie", "XSRF-TOKEN=(.*);[\\s]").saveAs("xsrf_token"))).exitHereIfFailed
        .exec((http("Submit Model2"))
            .put("/api/exercises/${exercise_id}/modeling-submissions")
            .headers(headers_http_authenticated_JSON)
            .body(StringBody("""{"submissionExerciseType":"modeling","submitted":true,"model":"""" + model1 + """"}""")).asJson
            .check(status.is(200)))

    val submitModels = scenario("Submitting both Models")
        .exec(firstModel)
        .exec(secondModel)

    setUp(
        submitModels.inject(atOnceUsers(1))
    ).protocols(httpProtocol)
}
