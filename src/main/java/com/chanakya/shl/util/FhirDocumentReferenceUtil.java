package com.chanakya.shl.util;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.Base64;
import java.util.Set;

public final class FhirDocumentReferenceUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> SHL_COMPLIANT_TYPES = Set.of(
            "application/smart-health-card",
            "application/smart-api-access",
            "application/fhir+json"
    );

    private FhirDocumentReferenceUtil() {}

    public static boolean isShlCompliantContentType(String contentType) {
        if (contentType == null) return false;
        String baseType = contentType.split(";")[0].strip();
        return SHL_COMPLIANT_TYPES.contains(baseType);
    }

    public static String wrapInDocumentReference(byte[] fileBytes, String contentType, String fileName) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("resourceType", "DocumentReference");
        root.put("status", "current");

        ObjectNode attachment = MAPPER.createObjectNode();
        attachment.put("contentType", contentType);
        attachment.put("data", Base64.getEncoder().encodeToString(fileBytes));
        if (fileName != null) {
            attachment.put("title", fileName);
        }

        ObjectNode contentEntry = MAPPER.createObjectNode();
        contentEntry.set("attachment", attachment);

        ArrayNode contentArray = root.putArray("content");
        contentArray.add(contentEntry);

        return root.toString();
    }
}
