package org.endeavourhealth.getFHIRRecordAPI.api.process;

import com.fasterxml.jackson.databind.JsonNode;
import models.ConfigDetails;
import models.Parameter;
import models.Params;
import models.ResourceNotFoundException;
import org.endeavourhealth.common.config.ConfigManager;
import org.endeavourhealth.core.database.dal.usermanager.caching.ProjectCache;
import org.endeavourhealth.core.database.dal.usermanager.caching.UserCache;
import org.endeavourhealth.core.database.rdbms.datasharingmanager.models.ProjectEntity;
import org.endeavourhealth.core.database.rdbms.usermanager.models.UserProjectEntity;
import org.endeavourhealth.getFHIRRecordAPI.common.models.ObservationFull;
import org.endeavourhealth.getFHIRRecordAPI.common.models.PatientFull;
import org.endeavourhealth.getFHIRRecordAPI.common.models.TelecomFull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public class FhirUtil {
    private static final Logger LOG = LoggerFactory.getLogger(FhirUtil.class);
    private static final String CONFIG_ID_RUN_MODE = "run_mode";
    private static final String CONFIG_ID_RUN_MODE_TEST = "test";
    public static FhirBundleParams readFhirConfigs(FhirBundleParams fbp) {
        try {
            fbp.setFhirConfigJsonNode(ConfigManager.getConfigurationAsJson(CONFIG_ID_RUN_MODE));
            fbp.setRunMode( fbp.getFhirConfigJsonNode().get("mode").asText());
            fbp.setUseDSM(fbp.getFhirConfigJsonNode().get("useDSM").asBoolean());
            LOG.info("using DSM = " + fbp.isUseDSM());

            HashMap<String, Map<String,String>> valueSetCodeMap=new HashMap<String,Map<String,String>>();
            JsonNode configMappingsNodes =fbp.getFhirConfigJsonNode().get("value_set_codes");
            if (configMappingsNodes != null) {
                for (int i = 0; i < configMappingsNodes.size(); i++) {
                    JsonNode configMappingNode = configMappingsNodes.get(i);
                    Map vmap= new HashMap<String,String>();
                    vmap.put("code",configMappingNode.get("code").asText());
                    vmap.put("text",configMappingNode.get("text").asText());
                    valueSetCodeMap.put(configMappingNode.get("id").asText(),vmap);
                }
            }

            fbp.setValueSetCodeMap(valueSetCodeMap);

        } catch (Exception e) {
            LOG.error(e.getMessage());
        }

        return fbp;
    }


    public static FhirBundleParams extractParamsFromJsonPayload(Params params, FhirBundleParams fbp) throws ResourceNotFoundException, IOException {
        List<Parameter> parameters = params.getParameter();
        for (Parameter param : parameters) {
            String paramName = param.getName();
            LOG.info("Received Parameter Name :" + param.getName());
            if (paramName.equals("patientNHSNumber")) {
                fbp.setNhsNumber( param.getValueIdentifier().getValue());
                LOG.info("Value Identifier : " +fbp.getNhsNumber());
                //test mode
                if (fbp.getRunMode() != null && fbp.getRunMode().equals(CONFIG_ID_RUN_MODE_TEST)) {
                    fbp.setOriginalNHSNumber(fbp.getNhsNumber());
                    fbp.setNhsNumber( getMappedTestNHSNumber(fbp));
                }

            } else if (paramName.equals("patientDOB")) {
                LOG.info("Value Identifier : " + param.getValueIdentifier().getValue());
                if (fbp.getRunMode() != null && !fbp.getRunMode().equals(CONFIG_ID_RUN_MODE_TEST)) {
                    fbp.setDateOfBirth( param.getValueIdentifier().getValue());
                }
            } else if (paramName.equals("demographicsOnly")) {
                if (param.getPart().get(0) != null && param.getPart().get(0).getValueBoolean()) {
                    LOG.info("Part value : " + param.getPart().get(0).getValueBoolean());
                    fbp.setOnlyDemographics(true);
                }
            } else if (paramName.equals("includeInactivePatients")) {
                if (param.getPart().get(0) != null && param.getPart().get(0).getValueBoolean()) {
                    LOG.info("Part value : " + param.getPart().get(0).getValueBoolean());
                    fbp.setActivePatientsOnly(false);
                }
            }
        }
        return fbp;
    }

    public static FhirBundleParams readDSMConfigsAndODSList(FhirBundleParams fbp, String userId) {
        if (fbp.isUseDSM()) {
            try {
                fbp.setDsmConfigDetails( checkUserAccessToOrganisations(userId,fbp));
            } catch (Exception e) {
                LOG.error(e.getMessage());
            }
        } else {
            // if running locally you might not have DSM fully set up so just take the config name to use from the runMode config entry.
            // Note, this should never be used in a live environment
            fbp.setDsmConfigDetails( getDevConfigName(fbp));
        }
        return fbp;
    }


    private static List<ConfigDetails> getDevConfigName(FhirBundleParams fbp) {
        ArrayList<ConfigDetails> configDetailList =new ArrayList<ConfigDetails>();
        try {
            String configName =fbp.getFhirConfigJsonNode().get("devConfigName").asText();
            String[] cfgs = configName.split(Pattern.quote("|"));
            for (int i = 0; i < cfgs.length; i++) {
                LOG.info("using dev config = " + cfgs[i]);
                ConfigDetails configdtl = new ConfigDetails();
                configdtl.setConfigName(cfgs[i]);
                configdtl.setDbtype( getMappedDBType(cfgs[i],fbp));
                configDetailList.add(configdtl);
            }

        } catch (Exception e) {
            LOG.error(e.getMessage());
        }
        return configDetailList;
    }

    private static  List<ConfigDetails> checkUserAccessToOrganisations(String userId,FhirBundleParams fbp) throws Exception {
        ArrayList<ConfigDetails> configDetailList =new ArrayList<ConfigDetails>();
        LOG.info("checkUserAccessToOrganisations for userId = " + userId);

        if (userId == null || !userId.equals("")) {

            List<UserProjectEntity> userProjects = UserCache.getUserProjectForUserId(userId);

            if (userProjects.size() < 1) {
                LOG.info("User is not assigned to a project...unable to find organisations");
                //1 Iain: need to raise a proper exception to mention user hs no valid projects configured in user_ management
                return configDetailList;
            }
            for (UserProjectEntity userProject : userProjects) {
                String projectId = userProject.getProjectId();

                LOG.info("checkUserAccessToOrganisations for projectId = " + projectId);

                List<String> orgList = ProjectCache.getAllPublishersForValidProject(projectId, true);

                ProjectEntity project = ProjectCache.getProjectDetails(projectId);

                if (project != null && project.getConfigName() != null && !project.getConfigName().isEmpty()) {
                    ConfigDetails configdtl = new ConfigDetails();
                    configdtl.setConfigName(project.getConfigName());
                    configdtl.setDbtype( getMappedDBType(project.getConfigName(),fbp));
                    configdtl.setValidOrgs(orgList);
                    configDetailList.add(configdtl);

                    LOG.info("config details name = " + project.getConfigName());
                    LOG.info("config details valid Orgs = " + orgList );
                }
            }
        }
        return configDetailList;
    }



    /*
     * Check if the mapping for input NHS number exists in the config table and return the mapped NHS number
     */
    private static String getMappedTestNHSNumber(FhirBundleParams fbp) throws ResourceNotFoundException {

        JsonNode nhsMappingsNodes = fbp.getFhirConfigJsonNode().get("nhs_mappings");
        String mappedNHSNumber = null;
        if (nhsMappingsNodes != null) {
            for (int i = 0; i < nhsMappingsNodes.size(); i++) {
                JsonNode nhsMappingNode = nhsMappingsNodes.get(i);
                String anonymisedNHSNumber = nhsMappingNode.get("anonymised").asText();
                if (fbp.getNhsNumber() != null && fbp.getNhsNumber().equals(anonymisedNHSNumber)) {
                    mappedNHSNumber = nhsMappingNode.get("mapped").asText();
                    return mappedNHSNumber;
                }
            }
        }
        if (mappedNHSNumber == null) {
            LOG.error("NHS number " + fbp.getNhsNumber() + " passed in is not in the mapping config");
            throw new ResourceNotFoundException("Resource error: NHS number " +
                    "" + fbp.getNhsNumber() + " passed in is not in the mapping config");
        }
        return null;
    }

    /*
     * Check if the mapping for input NHS number exists in the config table and return the mapped NHS number
     */
    private static String getMappedDBType(String configname,FhirBundleParams fbp) throws ResourceNotFoundException {
        JsonNode configMappingsNodes = fbp.getFhirConfigJsonNode().get("config_mappings");
        String dbType = null;
        if (configMappingsNodes != null) {
            for (int i = 0; i < configMappingsNodes.size(); i++) {
                JsonNode configMappingNode = configMappingsNodes.get(i);
                String mappedConfigname = configMappingNode.get("config").asText();
                if (configname != null && configname.equals(mappedConfigname)) {
                    dbType = configMappingNode.get("db_type").asText();
                    return dbType;
                }
            }
        }
        if (dbType == null) {
            LOG.error("dbType for " + configname + " not found in the configuration");
            throw new ResourceNotFoundException("Resource error: " + "dbType for " + configname + " not found in the configuration");
        }
        return null;
    }

    //same code observations (vital-signs) restrict to max 3.
    public static boolean filterVitalSigns(ObservationFull observationFull, Date baselineDate, HashMap<String, Integer> vitalSignsCounter) throws Exception {

        Date observationDate = observationFull.getDate();

        String code = observationFull.getCode();
        String category = observationFull.getCategoryText();

        if (!category.equalsIgnoreCase("vital-signs")) {
            return true;
        }

        Integer counter = vitalSignsCounter.get(code);
        if (counter == null) {
            counter = 0;
        }
        // if the observation is within the last year or if we have less than 3 then include it
        if (observationDate.after(baselineDate) || counter < 3) {
            vitalSignsCounter.put(code, ++counter);
            return true;
        }

        return false;
    }

    /*
     * Replace demographic info with xxxx and dob/dod with 09-Sep-9999
     */
    public static PatientFull hideDemographicInfo(PatientFull patient, String originalNHSNumber) {
        String morphString = "XXXX";
        patient.setFirstname(morphString);
        patient.setLastname(morphString);
        patient.setDob(new Date("09-Sep-9999"));
        patient.setAdd1(morphString);
        patient.setAdd2(morphString);
        patient.setAdd3(morphString);
        patient.setAdd4(morphString);
        patient.setPostcode(morphString);
        patient.setNhsNumber(originalNHSNumber);
        patient.setAdduse(morphString);
        patient.setCity(morphString);
        patient.setDod(new Date("09-Sep-9999"));
        patient.setGender("Unknown");

        patient.setEthnicDescription(morphString);

        if (patient.getTelecomFullList() != null && patient.getTelecomFullList().size() > 0) {
            for (TelecomFull telecomFull : patient.getTelecomFullList()) {
                telecomFull.setValue(morphString);
            }
        }

        return patient;

    }

    public static Date getBaselineDateForFilters(int pastyear) throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, pastyear);
        return cal.getTime();
    }

    public static String convertToTime(int i) {
        String scnds = "";
        String hours = "00";
        String minutes = "00";
        if (i > 3600) {
            hours = String.format("%02d", i / 3600);
            i = i % 3600;
        }
        if (i > 60) {
            minutes = String.format("%02d", i / 60);
            i = i % 60;

        }
        scnds = String.format("%02d", i);
        return " " + hours + ":" + minutes + ":" + scnds + "";
    }
}
