package org.endeavourhealth.getFHIRRecordAPI.api.endpoints;

import models.Params;
import models.Parameter;
import models.ValueIdentifier;
import org.endeavourhealth.getFHIRRecordAPI.api.process.FhirApi;
import org.endeavourhealth.getFHIRRecordAPI.api.process.PalantirBundleMapper;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.util.*;

@Path("api/v2/ontologies/nhs/objects")
public class PalantirObjectEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(PalantirObjectEndpoint.class);

    @GET
    @Path("patient/{primaryKey}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getObject(
            @PathParam("primaryKey") String primaryKey,
            @QueryParam("select") List<String> selectProperties,
            @QueryParam("excludeRid") Boolean excludeRid,
            @Context HttpServletRequest request) {

        LOG.info("Palantir GET Object called - primaryKey={}", primaryKey);

        String userId = extractUserId(request);
        LOG.info("User ID for Palantir request: {}", userId);

        if (userId == null || userId.isEmpty()) {
            return Response.status(401)
                    .entity("{\"error\":\"Missing or invalid authorization\"}")
                    .build();
        }

        try {
            Params params = buildParams(primaryKey);
            FhirApi fhirApi = new FhirApi();
            JSONObject bundleJson = fhirApi.handleRequest(params, userId);

            boolean excludeRidFlag = (excludeRid != null && excludeRid);
            Map<String, Object> palantirObject = PalantirBundleMapper.mapToFhirToPalantir(bundleJson, selectProperties, excludeRidFlag);

            return Response.ok().entity(palantirObject).build();

        } catch (Exception e) {
            LOG.error("Error processing Palantir GET Object request", e);
            String errorMsg = "Patient resource with id = '" + primaryKey + "' not found";
            if (e.getMessage() != null && !e.getMessage().contains("not found")) {
                errorMsg = "Resource error: " + e.getMessage();
            }
            return Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"" + errorMsg + "\"}")
                    .build();
        }
    }

    private String extractUserId(HttpServletRequest request) {
        // Try Authorization Bearer token first (standard Palantir API pattern)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return decodeJwtSubject(token);
        }

        // Fall back to user_id header for backward compatibility
        String userId = request.getHeader("user_id");
        if (userId != null && !userId.isEmpty()) {
            return userId;
        }

        return null;
    }

    private String decodeJwtSubject(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                LOG.warn("Invalid JWT token format");
                return null;
            }
            // Decode the payload (second part of JWT)
            byte[] decodedBytes = java.util.Base64.getUrlDecoder().decode(parts[1]);
            String payload = new String(decodedBytes, "UTF-8");

            // Extract the "sub" claim using simple string parsing
            int subIndex = payload.indexOf("\"sub\"");
            if (subIndex == -1) {
                LOG.warn("JWT token does not contain 'sub' claim");
                return null;
            }
            // Find the value after "sub":
            int colonOrCommaStart = payload.indexOf(':', subIndex);
            if (colonOrCommaStart == -1) {
                colonOrCommaStart = payload.indexOf(',', subIndex);
            }
            int quoteStart = payload.indexOf('"', colonOrCommaStart);
            int quoteEnd = payload.indexOf('"', quoteStart + 1);
            if (quoteStart != -1 && quoteEnd != -1) {
                return payload.substring(quoteStart + 1, quoteEnd);
            }

            LOG.warn("Could not extract 'sub' claim from JWT");
            return null;
        } catch (Exception e) {
            LOG.error("Failed to decode JWT token", e);
            return null;
        }
    }

    private Params buildParams(String nhsNumber) {
        Params params = new Params();
        params.setResourceType("Parameters");

        List<Parameter> parameters = new ArrayList<>();

        Parameter nhsParam = new Parameter();
        nhsParam.setName("patientNHSNumber");
        ValueIdentifier nhsValue = new ValueIdentifier();
        nhsValue.setValue(nhsNumber);
		nhsValue.setSystem("https://fhir.hl7.org.uk/Id/nhs-number");
        nhsParam.setValueIdentifier(nhsValue);
        parameters.add(nhsParam);
		
        params.setParameter(parameters);
        return params;
    }
}
