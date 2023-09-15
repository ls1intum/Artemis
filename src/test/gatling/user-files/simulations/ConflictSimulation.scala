package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import util.Properties

class ConflictSimulation extends Simulation {

    val httpProtocol = http
        .baseUrl("http://localhost:9000")
        .inferHtmlResources()
        .acceptHeader("*/*")
        .acceptEncodingHeader("gzip, deflate")
        .acceptLanguageHeader("en-US,en;q=0.5")
        .userAgentHeader("Mozilla/5.0 (X11; Fedora; Linux x86_64; rv:66.0) Gecko/20100101 Firefox/66.0")
        .silentResources

    val userCredentials: Array[(String, String)] = Array(("<username-1>", "<password-1>"), ("<username-2>", "<password-2>"))
    val model1: String = """{\"version\":\"2.0.0\",\"size\":{\"width\":910,\"height\":370},\"type\":\"ClassDiagram\",\"interactive\":{\"elements\":[],\"relationships\":[]},\"elements\":[{\"id\":\"62c4675c-dd53-4e5c-bc18-0042081b9ac7\",\"name\":\"Tea\",\"owner\":null,\"type\":\"Class\",\"bounds\":{\"x\":0,\"y\":270,\"width\":200,\"height\":100},\"attributes\":[\"e0133088-a2d2-4913-bfab-550182fbf77d\"],\"methods\":[\"dfe1c9e9-d4aa-4707-aedd-19b0406c2e20\"]},{\"id\":\"e0133088-a2d2-4913-bfab-550182fbf77d\",\"name\":\"+ temp: Type\",\"owner\":\"62c4675c-dd53-4e5c-bc18-0042081b9ac7\",\"type\":\"ClassAttribute\",\"bounds\":{\"x\":0,\"y\":310,\"width\":200,\"height\":30}},{\"id\":\"dfe1c9e9-d4aa-4707-aedd-19b0406c2e20\",\"name\":\"+ drink()\",\"owner\":\"62c4675c-dd53-4e5c-bc18-0042081b9ac7\",\"type\":\"ClassMethod\",\"bounds\":{\"x\":0,\"y\":340,\"width\":200,\"height\":30}},{\"id\":\"2ac69a3b-7e75-4010-8af5-0785fe3aaa4d\",\"name\":\"Class\",\"owner\":null,\"type\":\"Class\",\"bounds\":{\"x\":710,\"y\":270,\"width\":200,\"height\":100},\"attributes\":[\"b94f0549-cfcc-4069-9058-f66413b18cde\"],\"methods\":[\"5181da8e-6f29-4b46-97c3-2eca7e1ee1e3\"]},{\"id\":\"b94f0549-cfcc-4069-9058-f66413b18cde\",\"name\":\"+ attribute: Type\",\"owner\":\"2ac69a3b-7e75-4010-8af5-0785fe3aaa4d\",\"type\":\"ClassAttribute\",\"bounds\":{\"x\":710,\"y\":310,\"width\":200,\"height\":30}},{\"id\":\"5181da8e-6f29-4b46-97c3-2eca7e1ee1e3\",\"name\":\"+ method()\",\"owner\":\"2ac69a3b-7e75-4010-8af5-0785fe3aaa4d\",\"type\":\"ClassMethod\",\"bounds\":{\"x\":710,\"y\":340,\"width\":200,\"height\":30}},{\"id\":\"c430c7a2-046f-45d7-925f-a754c7a3190f\",\"name\":\"Drink\",\"owner\":null,\"type\":\"AbstractClass\",\"bounds\":{\"x\":360,\"y\":0,\"width\":200,\"height\":70},\"attributes\":[],\"methods\":[\"6819b6c6-12ed-4f07-98a4-94d76d7409bc\"]},{\"id\":\"6819b6c6-12ed-4f07-98a4-94d76d7409bc\",\"name\":\"+ drink()\",\"owner\":\"c430c7a2-046f-45d7-925f-a754c7a3190f\",\"type\":\"ClassMethod\",\"bounds\":{\"x\":360,\"y\":40,\"width\":200,\"height\":30}}],\"relationships\":[],\"assessments\":[]}"""
    val assessmentModel1:String ="""[{"referenceId":"c430c7a2-046f-45d7-925f-a754c7a3190f","referenceType":"AbstractClass","reference":"AbstractClass:c430c7a2-046f-45d7-925f-a754c7a3190f","credits":0.5},{"referenceId":"6819b6c6-12ed-4f07-98a4-94d76d7409bc","referenceType":"ClassMethod","reference":"ClassMethod:6819b6c6-12ed-4f07-98a4-94d76d7409bc","credits":0.5},{"referenceId":"2ac69a3b-7e75-4010-8af5-0785fe3aaa4d","referenceType":"Class","reference":"Class:2ac69a3b-7e75-4010-8af5-0785fe3aaa4d","credits":0.5},{"referenceId":"b94f0549-cfcc-4069-9058-f66413b18cde","referenceType":"ClassAttribute","reference":"ClassAttribute:b94f0549-cfcc-4069-9058-f66413b18cde","credits":0.5},{"referenceId":"5181da8e-6f29-4b46-97c3-2eca7e1ee1e3","referenceType":"ClassMethod","reference":"ClassMethod:5181da8e-6f29-4b46-97c3-2eca7e1ee1e3","credits":0.5},{"referenceId":"62c4675c-dd53-4e5c-bc18-0042081b9ac7","referenceType":"Class","reference":"Class:62c4675c-dd53-4e5c-bc18-0042081b9ac7","credits":0.5},{"referenceId":"e0133088-a2d2-4913-bfab-550182fbf77d","referenceType":"ClassAttribute","reference":"ClassAttribute:e0133088-a2d2-4913-bfab-550182fbf77d","credits":0.5},{"referenceId":"dfe1c9e9-d4aa-4707-aedd-19b0406c2e20","referenceType":"ClassMethod","reference":"ClassMethod:dfe1c9e9-d4aa-4707-aedd-19b0406c2e20","credits":0.5}]"""
    val model2: String = """{\"version\":\"2.0.0\",\"size\":{\"width\":1410,\"height\":530},\"type\":\"ClassDiagram\",\"interactive\":{\"elements\":[],\"relationships\":[]},\"elements\":[{\"id\":\"753f3d0d-73ac-433a-8318-76ab56adc846\",\"name\":\"Drink\",\"owner\":null,\"type\":\"AbstractClass\",\"bounds\":{\"x\":0,\"y\":50,\"width\":200,\"height\":100},\"attributes\":[\"54f52912-5c7e-4401-9788-3f3de239e715\"],\"methods\":[\"e15ca6cb-10c0-42fc-827e-b77f268c4f4c\"]},{\"id\":\"54f52912-5c7e-4401-9788-3f3de239e715\",\"name\":\"+ nothing\",\"owner\":\"753f3d0d-73ac-433a-8318-76ab56adc846\",\"type\":\"ClassAttribute\",\"bounds\":{\"x\":0,\"y\":90,\"width\":200,\"height\":30}},{\"id\":\"e15ca6cb-10c0-42fc-827e-b77f268c4f4c\",\"name\":\"+ drink()\",\"owner\":\"753f3d0d-73ac-433a-8318-76ab56adc846\",\"type\":\"ClassMethod\",\"bounds\":{\"x\":0,\"y\":120,\"width\":200,\"height\":30}},{\"id\":\"01f98b8b-3139-44ad-a783-fa43d51dcdba\",\"name\":\"Tea\",\"owner\":null,\"type\":\"Class\",\"bounds\":{\"x\":1210,\"y\":0,\"width\":200,\"height\":100},\"attributes\":[\"7fd3f82d-1a8f-4d1b-82ab-84e3df93be84\"],\"methods\":[\"0d44e404-1dde-4c40-aedb-ae60af5244a2\"]},{\"id\":\"7fd3f82d-1a8f-4d1b-82ab-84e3df93be84\",\"name\":\"+ temp: Type\",\"owner\":\"01f98b8b-3139-44ad-a783-fa43d51dcdba\",\"type\":\"ClassAttribute\",\"bounds\":{\"x\":1210,\"y\":40,\"width\":200,\"height\":30}},{\"id\":\"0d44e404-1dde-4c40-aedb-ae60af5244a2\",\"name\":\"+ drink()\",\"owner\":\"01f98b8b-3139-44ad-a783-fa43d51dcdba\",\"type\":\"ClassMethod\",\"bounds\":{\"x\":1210,\"y\":70,\"width\":200,\"height\":30}},{\"id\":\"0b396708-3561-4fdb-b6d6-b04020194d07\",\"name\":\"Coffee\",\"owner\":null,\"type\":\"Class\",\"bounds\":{\"x\":870,\"y\":430,\"width\":200,\"height\":100},\"attributes\":[\"c1a04810-a52f-42d5-b100-872b85f0cc89\"],\"methods\":[\"7eb695c9-5b67-4b74-abf2-aad998be850c\"]},{\"id\":\"c1a04810-a52f-42d5-b100-872b85f0cc89\",\"name\":\"+ temp: Type\",\"owner\":\"0b396708-3561-4fdb-b6d6-b04020194d07\",\"type\":\"ClassAttribute\",\"bounds\":{\"x\":870,\"y\":470,\"width\":200,\"height\":30}},{\"id\":\"7eb695c9-5b67-4b74-abf2-aad998be850c\",\"name\":\"+ drink()\",\"owner\":\"0b396708-3561-4fdb-b6d6-b04020194d07\",\"type\":\"ClassMethod\",\"bounds\":{\"x\":870,\"y\":500,\"width\":200,\"height\":30}}],\"relationships\":[{\"id\":\"40f12b68-b42f-42a1-8bae-623dc58897ee\",\"name\":\"\",\"type\":\"ClassBidirectional\",\"source\":{\"element\":\"0b396708-3561-4fdb-b6d6-b04020194d07\",\"direction\":\"Left\",\"multiplicity\":\"\",\"role\":\"\"},\"target\":{\"element\":\"753f3d0d-73ac-433a-8318-76ab56adc846\",\"direction\":\"Right\",\"multiplicity\":\"\",\"role\":\"\"},\"path\":[{\"x\":672,\"y\":382},{\"x\":337,\"y\":382},{\"x\":337,\"y\":2},{\"x\":2,\"y\":2}],\"bounds\":{\"x\":198,\"y\":98,\"width\":674,\"height\":384}},{\"id\":\"155256b5-0cf7-4dbf-8e2a-cab00ef97a67\",\"name\":\"\",\"type\":\"ClassBidirectional\",\"source\":{\"element\":\"01f98b8b-3139-44ad-a783-fa43d51dcdba\",\"direction\":\"Left\",\"multiplicity\":\"\",\"role\":\"\"},\"target\":{\"element\":\"753f3d0d-73ac-433a-8318-76ab56adc846\",\"direction\":\"Right\",\"multiplicity\":\"\",\"role\":\"\"},\"path\":[{\"x\":1012,\"y\":2},{\"x\":2,\"y\":2}],\"bounds\":{\"x\":198,\"y\":73,\"width\":1014,\"height\":4}}],\"assessments\":[]}"""

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

    val firstModel = scenario("Submit and assess 1. Model")
        .exec(http("First unauthenticated request")
            .get("/api/public/account")
            .headers(headers_http)
            .check(status.is(401))
            .check(headerRegex("set-cookie", "XSRF-TOKEN=(.*);[\\s]").saveAs("xsrf_token"))).exitHereIfFailed
        .exec(http("Authentication of 1. User")
            .post("/api/public/authenticate")
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
            .body(StringBody("""{"isAtLeastTutor":false,"isAtLeastInstructor":false,"type":"modeling","course":{"id":""" + "${course_id}" + ""","title":"CourseXY","shortName":"TTTXY","studentGroupName":"tumuser","instructorGroupName":"tumuser","onlineCourse":false,"registrationEnabled":false,"startDate":null,"endDate":null,"exercises":[]},"diagramType":"ClassDiagram","title":"Exercise 1","maxScore":10,"problemStatement":"","releaseDate":null,"dueDate":null,"assessmentDueDate":null}""")).asJson
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
            .body(StringBody("""{"submissionExerciseType":"modeling","participation":${participation},"submitted":true,"model":"""" + model1 + """"}""")).asJson
            .check(status.is(200))
            .check(jsonPath("$.id").saveAs("submission_id"))
            .check(headerRegex("set-cookie", "XSRF-TOKEN=(.*);[\\s]").saveAs("xsrf_token"))).exitHereIfFailed
        .exec((http("Assess Model"))
            .put("/api/modeling-submissions/${submission_id}/assessment")
            .queryParam("submit","true")
            .headers(headers_http_authenticated_JSON)
            .body(StringBody(assessmentModel1)).asJson
            .check(status.is(200)))


    val secondModel = scenario("Submit 2nd. Model")
        .exec(flushSessionCookies)
        .exec(http("First unauthenticated request")
            .get("/api/public/account")
            .headers(headers_http)
            .check(status.is(401))
            .check(headerRegex("set-cookie", "XSRF-TOKEN=(.*);[\\s]").saveAs("xsrf_token"))).exitHereIfFailed
        .exec(http("Authentication of 2nd User")
            .post("/api/public/authenticate")
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
            .body(StringBody("""{"submissionExerciseType":"modeling","submitted":true,"model":"""" + model2 + """"}""")).asJson
            .check(status.is(200)))

    val submitModels = scenario("Submitting both Models")
        .exec(firstModel)
        .exec(secondModel)

    setUp(
        submitModels.inject(atOnceUsers(1))
    ).protocols(httpProtocol)
}
