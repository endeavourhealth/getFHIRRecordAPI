package org.endeavourhealth.getFHIRRecordAPI.api.process;

import com.fasterxml.jackson.databind.JsonNode;
import models.ConfigDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FhirBundleParams {
    private JsonNode fhirConfigJsonNode;
    private String nhsNumber = "0";
    private String originalNHSNumber;
    private boolean onlyDemographics = false;
    private boolean activePatientsOnly = true;
    private String dateOfBirth = "0";
    private boolean useDSM;
    private String runMode;
    private Map<String, Map<String, String>> valueSetCodeMap;
    private List<ConfigDetails> dsmConfigDetails = null;

    private static final Logger LOG = LoggerFactory.getLogger(FhirBundleParams.class);

    public JsonNode getFhirConfigJsonNode() {
        return fhirConfigJsonNode;
    }

    public void setFhirConfigJsonNode(JsonNode fhirConfigJsonNode) {
        this.fhirConfigJsonNode = fhirConfigJsonNode;
    }


    public List<ConfigDetails> getDsmConfigDetails() {
        return dsmConfigDetails;
    }

    public void setDsmConfigDetails(List<ConfigDetails> dsmConfigDetails) {
        this.dsmConfigDetails = dsmConfigDetails;
    }

    public String getOriginalNHSNumber() {
        return originalNHSNumber;
    }

    public void setOriginalNHSNumber(String originalNHSNumber) {
        this.originalNHSNumber = originalNHSNumber;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public boolean isOnlyDemographics() {
        return onlyDemographics;
    }

    public void setOnlyDemographics(boolean onlyDemographics) {
        this.onlyDemographics = onlyDemographics;
    }

    public boolean isActivePatientsOnly() {
        return activePatientsOnly;
    }

    public void setActivePatientsOnly(boolean activePatientsOnly) {
        this.activePatientsOnly = activePatientsOnly;
    }

    public boolean isUseDSM() {
        return useDSM;
    }

    public void setUseDSM(boolean useDSM) {
        this.useDSM = useDSM;
    }

    public String getRunMode() {
        return runMode;
    }

    public void setRunMode(String runMode) {
        this.runMode = runMode;
    }

    public Map<String, Map<String, String>> getValueSetCodeMap() {
        return valueSetCodeMap;
    }

    public void setValueSetCodeMap(Map<String, Map<String, String>> valueSetCodeMap) {
        this.valueSetCodeMap = valueSetCodeMap;
    }


    public String getNhsNumber() {
        return nhsNumber;
    }

    public void setNhsNumber(String nhsNumber) {
        this.nhsNumber = nhsNumber;
    }


    public void reorderConfigList() {

        if (null != getDsmConfigDetails() && getDsmConfigDetails().size() > 1) {
            List<ConfigDetails> orderedConfigDetails = new ArrayList<ConfigDetails>();
            List<ConfigDetails> tempconfigList = getDsmConfigDetails();

            String[] config_mappings = {"GP", "COMMUNITY", "ACUTE"};

            for (int j = 0; j < 3; j++) {

                for (int i = 0; i < getDsmConfigDetails().size(); i++) {
                    if (getDsmConfigDetails().get(i).getDbtype().equalsIgnoreCase(config_mappings[j])) {
                        orderedConfigDetails.add(getDsmConfigDetails().get(i));
                    }
                }

            }//for
            setDsmConfigDetails(orderedConfigDetails);
        }
    }
}
