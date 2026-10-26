package com.akira.educacion.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Helpers de la Fase 5A para representantes, plantillas y comunicaciones.
 */
public abstract class CommunicationTestSupport extends SchoolTestSupport {

    protected String createRepresentativeWithPhone(String token, String fullName, String phone) throws Exception {
        String phoneJson = phone == null ? "null" : "\"" + jsonEscape(phone) + "\"";
        MvcResult result = mockMvc.perform(post("/api/v1/representatives")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "fullName":"%s",
                          "phone":%s,
                          "email":null,
                          "relationship":"Madre"
                        }
                        """.formatted(jsonEscape(fullName), phoneJson)))
                .andReturn();
        return readId(result);
    }

    protected ResultActions linkRepresentative(String token, String studentId, String representativeId,
            boolean authorizedForCommunications) throws Exception {
        return mockMvc.perform(post("/api/v1/students/" + studentId + "/representatives")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "representativeId":"%s",
                          "primaryContact":true,
                          "authorizedForCommunications":%s,
                          "authorizedForBilling":false
                        }
                        """.formatted(representativeId, authorizedForCommunications)));
    }

    protected ResultActions createTemplate(String token, String code, String name, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/communications/templates")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "code":%s,
                          "name":"%s",
                          "body":"%s",
                          "active":true
                        }
                        """.formatted(code == null ? "null" : "\"" + jsonEscape(code) + "\"",
                        jsonEscape(name), jsonEscape(body))));
    }

    protected ResultActions previewCommunication(String token, String studentId, String representativeId,
            String templateId, boolean includeFinancialInfo, String financialNote) throws Exception {
        String note = financialNote == null ? "null" : "\"" + jsonEscape(financialNote) + "\"";
        return mockMvc.perform(post("/api/v1/communications/preview")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "studentId":"%s",
                          "representativeId":"%s",
                          "templateId":"%s",
                          "includeFinancialInfo":%s,
                          "financialNote":%s
                        }
                        """.formatted(studentId, representativeId, templateId, includeFinancialInfo, note)));
    }

    protected ResultActions prepareCommunication(String token, String studentId, String representativeId,
            String templateId, boolean includeFinancialInfo, String financialNote, boolean draft) throws Exception {
        String note = financialNote == null ? "null" : "\"" + jsonEscape(financialNote) + "\"";
        return mockMvc.perform(post("/api/v1/communications")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "studentId":"%s",
                          "representativeId":"%s",
                          "templateId":"%s",
                          "includeFinancialInfo":%s,
                          "financialNote":%s,
                          "saveAsDraft":%s
                        }
                        """.formatted(studentId, representativeId, templateId, includeFinancialInfo, note, draft)));
    }

    protected static String jsonEscape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    protected static String readString(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
