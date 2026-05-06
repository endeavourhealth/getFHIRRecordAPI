package org.endeavourhealth.getFHIRRecordAPI.api.endpoints;

import com.google.gson.Gson;
import models.Params;
import org.endeavourhealth.getFHIRRecordAPI.api.process.FhirApi;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.util.*;

@Path("patient")
public class CareRecordEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(CareRecordEndpoint.class);

    @POST
    @Path("/$getstructuredrecord")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response postFhir(@Context HttpServletRequest httpServletRequest) throws Exception {
        LOG.debug("getFhir POST");
        LOG.info("in the API now");
        String userId = httpServletRequest.getHeader("user_id");

        String request = extractRequestBody(httpServletRequest);

        Gson g = new Gson();
        Params params = g.fromJson(request, Params.class);


        FhirApi api = new FhirApi();
        try {
            JSONObject result = api.handleRequest(params, userId);

            return Response
                    .ok()
                    .entity(result)
                    .build();
        } catch (Exception e) {

            return Response
                    .serverError()
                    .entity(e.getMessage())
                    .build();
        }
    }


    static String extractRequestBody(HttpServletRequest request) {
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            Scanner s = null;
            try {
                s = new Scanner(request.getInputStream(), "UTF-8").useDelimiter("\\A");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return s.hasNext() ? s.next() : "";
        }
        return "";
    }



}


