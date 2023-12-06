package de.tum.in.www1.artemis.util;

public class LtiRequestBodies {

    public static final String EDX_REQUEST_BODY = """
            custom_component_display_name=Exercise\
            &lti_version=LTI-1p0\
            &oauth_nonce=171298047571430710991572204884\
            &resource_link_id=courses.edx.org-16a90aca094448ab95caf484b5c35d32\
            &context_id=course-v1%3ATUMx%2BSEECx%2B1T2018\
            &oauth_signature_method=HMAC-SHA1\
            &oauth_timestamp=1572204884\
            &lis_person_contact_email_primary=__EMAIL__\
            &oauth_signature=GYXApaIv0x7k%2FOPT9%2FoU38IBQRc%3D\
            &context_title=Software+Engineering+Essentials\
            &lti_message_type=basic-lti-launch-request\
            &launch_presentation_return_url=\
            &context_label=TUMx\
            &user_id=ff30145d6884eeb2c1cef50298939383\
            &roles=Student\
            &oauth_version=1.0\
            &oauth_consumer_key=artemis_lti_key\
            &lis_result_sourcedid=course-v1%253ATUMx%252BSEECx%252B1T2018%3Acourses.edx.org-16a90aca094448ab95caf484b5c35d32%3Aff30145d6884eeb2c1cef50298939383\
            &launch_presentation_locale=en\
            &lis_outcome_service_url=https%3A%2F%2Fcourses.edx.org%2Fcourses%2Fcourse-v1%3ATUMx%2BSEECx%2B1T2018%2Fxblock%2Fblock-v1%3ATUMx%2BSEECx%2B1T2018%2Btype%40lti_consumer%2Bblock%4016a90aca094448ab95caf484b5c35d32%2Fhandler_noauth%2Foutcome_service_handler\
            &lis_person_sourcedid=lovaiible\
            &oauth_callback=about%3Ablank""";

    public static final String MOODLE_REQUEST_BODY = """
            oauth_version=1.0\
            &oauth_timestamp=1659585343\
            &oauth_nonce=ce994a9669026380ec4d2c6e2722460a\
            &oauth_consumer_key=artemis_lti_key\
            &user_id=11\
            &lis_person_sourcedid=\
            &roles=Learner\
            &context_id=3\
            &context_label=MO1\
            &context_title=TestCourseNonTUMX\
            &resource_link_title=LTI\
            &resource_link_description=\
            &resource_link_id=5\
            &context_type=CourseSection\
            &lis_course_section_sourcedid=\
            &lis_result_sourcedid=%7B%22data%22%3A%7B%22instanceid%22%3A%225%22%2C%22userid%22%3A%2211%22%2C%22typeid%22%3A%225%22%2C%22launchid%22%3A1792115554%7D%2C%22hash%22%3A%22d7a145eb9d0afd5aeff342de0b8a10ddd8b2344bbdcf544b0af580cc3209d636%22%7D\
            &lis_outcome_service_url=http%3A%2F%2Flocalhost%3A81%2Fmod%2Flti%2Fservice.php\
            &lis_person_name_given=carlos\
            &lis_person_name_family=moodle\
            &lis_person_name_full=carlos+moodle\
            &ext_user_username=carlosmoodle\
            &lis_person_contact_email_primary=__EMAIL__\
            &launch_presentation_locale=en\
            &ext_lms=moodle-2\
            &tool_consumer_info_product_family_code=moodle\
            &tool_consumer_info_version=2021051707\
            &oauth_callback=about%3Ablank\
            &lti_version=LTI-1p0\
            &lti_message_type=basic-lti-launch-request\
            &tool_consumer_instance_guid=localhost\
            &tool_consumer_instance_name=New+Site\
            &tool_consumer_instance_description=New+Site\
            &launch_presentation_document_target=window\
            &launch_presentation_return_url=http%3A%2F%2Flocalhost%3A81%2Fmod%2Flti%2Freturn.php%3Fcourse%3D3%26launch_container%3D4%26instanceid%3D5%26sesskey%3DBG6zIkjI4p\
            &oauth_signature_method=HMAC-SHA1\
            &oauth_signature=nj33KzZAyM%2Fg%2B3R1TVfQwpt7mPk%3D""";

}
