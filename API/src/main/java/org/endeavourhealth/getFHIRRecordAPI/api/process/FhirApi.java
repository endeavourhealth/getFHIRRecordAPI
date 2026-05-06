package org.endeavourhealth.getFHIRRecordAPI.api.process;

import ca.uhn.fhir.context.FhirContext;
import models.ConfigDetails;
import models.Params;
import models.ResourceNotFoundException;
import org.endeavourhealth.getFHIRRecordAPI.common.constants.ResourceConstants;
import org.endeavourhealth.getFHIRRecordAPI.common.dal.JDBCDAL;
import org.endeavourhealth.getFHIRRecordAPI.common.models.*;
import org.hl7.fhir.dstu3.model.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import resources.AllergyIntolerance;
import resources.Appointment;
import resources.Condition;
import resources.Encounter;
import resources.FamilyMemberHistory;
import resources.Immunization;
import resources.MedicationStatement;
import resources.Observation;
import resources.Organization;
import resources.Patient;
import resources.Practitioner;
import resources.Procedure;
import resources.ReferralRequest;
import resources.*;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static resources.Alert.getAlertResource;

public class FhirApi {
    private static final Logger LOG = LoggerFactory.getLogger(FhirApi.class);

    private static final String CONFIG_ID_RUN_MODE_TEST = "test";
    Bundle bundle = null;
    org.hl7.fhir.dstu3.model.Patient patientResource;
    HashMap<Long, Resource> organizationFhirMap;
    HashMap<Long, Resource> encounterFhirMap;
    Map<String, Resource> practitionerResourcesMap;
    //Map<Long, Resource> practitionerRoleResourcesMap;
    Map<Long, Coding> patientCodingMap;
    Set<String> observationIds;

    public JSONObject handleRequest(Params params, String userId) throws ResourceNotFoundException, IOException {
        JSONObject json = null;
        FhirBundleParams fbp = new FhirBundleParams();
        fbp = FhirUtil.readFhirConfigs(fbp);
        fbp = FhirUtil.readDSMConfigsAndODSList(fbp, userId);
        fbp = FhirUtil.extractParamsFromJsonPayload(params, fbp);
        try {
            json = prepareFhirBundleJSON(fbp);

        } catch (Exception e) {
            e.printStackTrace();
            throw new ResourceNotFoundException("Resource error:" + e);
        }
        return json;
    }

    public JSONObject prepareFhirBundleJSON(FhirBundleParams fbp) throws Exception {
        fbp.reorderConfigList();
        if (null != fbp.getDsmConfigDetails() && fbp.getDsmConfigDetails().size() > 0) {
            for (ConfigDetails cfgdtls : fbp.getDsmConfigDetails()) {
                String dbtype = cfgdtls.getDbtype();
                try (JDBCDAL viewerDAL = new JDBCDAL()) {
                    clearGlobalResourceMaps();
                    PatientFull patient = null;
                    viewerDAL.setConnectionAndOrgs(cfgdtls.getValidOrgs(), cfgdtls.getConfigName());
                    patient = viewerDAL.getPatientFull(fbp.getNhsNumber(), fbp.getDateOfBirth(), fbp.isActivePatientsOnly(), fbp.isUseDSM(), dbtype);
                    if (patient == null) {
                        LOG.info("Patient not found" + cfgdtls.getDbtype());
                        continue;
                    }
                    LOG.info("Found Patient in" + cfgdtls.getDbtype());
                    if (patientResource == null) {
                        //hide  Demographic details for run mode = test
                        if (fbp.getRunMode() != null && fbp.getRunMode().equals(CONFIG_ID_RUN_MODE_TEST)) {
                            FhirUtil.hideDemographicInfo(patient, fbp.getOriginalNHSNumber());
                        }
                        patientResource = Patient.getPatientResource(patient, viewerDAL);

                        setOrgAndPractitiornerToPatientFhir(patientResource, patient, viewerDAL);
                        if (bundle == null) {
                            bundle = new Bundle();
                            setTypeAndMeta(bundle);
                            bundle.addEntry().setResource(patientResource);
                            if (fbp.isOnlyDemographics()) {
                                return parseBundle();
                            }
                        }
                    }// if(patientResource==null)
                    Map<Long, String> patientMap;
                    List<Long> patientIds = null;

                    patientMap = viewerDAL.getPatientIds(fbp.getNhsNumber(), fbp.isUseDSM());
                    patientIds = patientMap.keySet().stream().collect(Collectors.toList());
                    setCoding(patientMap);

                    if (dbtype.equalsIgnoreCase("ACUTE")) {
                        addFhirConditionsToBundle(patientIds, viewerDAL, dbtype);
                        addAlertsToBundle(patientIds, viewerDAL);
                        addFhirAppointmentToBundle(patientIds, viewerDAL);
                        addFhirEncountersToBundle(patientIds, viewerDAL, dbtype);
                        addProcedureToBundle(patientIds, viewerDAL);
                        addToBundle("organizations");
                        LOG.info("Got obs");
                        addToBundle("practitioners");


                    } else {
                        addClinicalDataToBundle(patientIds, viewerDAL, dbtype, fbp);

                    }
                }//CONFIG
            }
            if (null != bundle) {
                return parseBundle();
            } else {
                throw new ResourceNotFoundException("Patient resource with id = '" + fbp.getNhsNumber() + "' not found");
            }
        } else {
            throw new ResourceNotFoundException("Resource error:" + "DSA subscriber config not found");
        }


    }

    private void setTypeAndMeta(Bundle bundle) {
        bundle.setType(Bundle.BundleType.COLLECTION);
        Meta meta = new Meta();
        meta.addProfile("https://fhir.hl7.org.uk/STU3/StructureDefinition/CareConnect-StructuredRecord-Bundle-1");
        bundle.setMeta(meta);
    }

    private void setOrgAndPractitiornerToPatientFhir(org.hl7.fhir.dstu3.model.Patient patientResource, PatientFull patient, JDBCDAL viewerDAL) throws Exception {
        if (patient.getOrglocation().trim().length() > 0) {
            patientResource.setManagingOrganization(new Reference(getOrganizationFhirObj(Long.parseLong(patient.getOrglocation()), viewerDAL)));
        }
        if (patient.getPractitionerId() != 0) {
            org.hl7.fhir.dstu3.model.Practitioner practitioner = getPractitionerResource(patient.getPractitionerId(), viewerDAL);
            if (practitioner != null) {
                patientResource.setGeneralPractitioner(Arrays.asList(new Reference(practitioner)));
            }
        }
    }

    private void addClinicalDataToBundle(List<Long> patientIds, JDBCDAL viewerDAL, String dbtype, FhirBundleParams fbp) throws Exception {
        // set the baseline date for filters to use
        LOG.info("Fetching Observations");
        addObservationToBundle(patientIds, viewerDAL, dbtype, fbp.getValueSetCodeMap());

        LOG.info("Fetching Conditions");
        addFhirConditionsToBundle(patientIds, viewerDAL, dbtype);

        LOG.info("Fetching Allergies");
        addFhirAllergiesToBundle(patientIds, viewerDAL);

        LOG.info("Fetching MedicationStatement");
        addFhirMedicationStatementToBundle(patientIds, viewerDAL);

        LOG.info("Fetching FamilyMemberHistory");
        addFhirFamilyMemberHistoryToBundle(patientIds, viewerDAL);

        LOG.info("Fetching Encounters");
        addFhirEncountersToBundle(patientIds, viewerDAL, dbtype);

        LOG.info("Fetching Immunizations");
        addFhirImmunizationsToBundle(patientIds, viewerDAL, dbtype);

        LOG.info("Fetching ReferralRequests");
        addFhirReferralRequestsToBundle(patientIds, viewerDAL);

        addToBundle("organizations");
        addToBundle("practitioners");
    }

    private void clearGlobalResourceMaps() {
        organizationFhirMap = new HashMap<>();
        encounterFhirMap = new HashMap<>();
        patientCodingMap = new HashMap<>();
        practitionerResourcesMap = new HashMap<>();

        observationIds = new HashSet<>();
    }

    private void addAlertsToBundle(List<Long> patientIds, JDBCDAL viewerDAL) throws Exception {
        List<AlertFull> alerts = viewerDAL.getAlertFullList(patientIds);
        if (alerts.size() > 0) {
            for (AlertFull alertfull : alerts) {
                Flag alert = getAlertResource(alertfull);
                alert.setSubject(new Reference(patientResource));
                alert.getMeta().addTag(getMetaOrgCoding(alertfull.getOrganization_id(),getOrgname(Long.parseLong(alertfull.getOrganization_id()),viewerDAL)));
               // alert.getMeta().addTag(patientCodingMap.get((alertfull.getPatient_id())));
                bundle.addEntry().setResource(alert);
            }
        }
    }



    private org.hl7.fhir.dstu3.model.Practitioner getPractitionerResource(Long practitionerId, JDBCDAL viewerDAL) throws Exception {

        org.hl7.fhir.dstu3.model.Practitioner practitionerResource = null;

        if (practitionerResourcesMap.get(practitionerId+"") != null) {
            return (org.hl7.fhir.dstu3.model.Practitioner) practitionerResourcesMap.get(practitionerId+"");
        } else {
            PractitionerFull practitionerResult = viewerDAL.getPractitionerFull(practitionerId);
            if (practitionerResult != null) {
                practitionerResource = Practitioner.getPractitionerResource(practitionerResult);
                practitionerResourcesMap.put(practitionerId+"", practitionerResource);
            }
            return practitionerResource;
        }
    }




    private void addFhirConditionsToBundle(List<Long> patientIds, JDBCDAL viewerDAL, String dbType) throws Exception {
        List<ConditionFull> conditions = viewerDAL.getConditionFullList(patientIds, dbType);
        if (conditions.size() > 0) {
            LOG.info("Got conditions");
            //create AllergiesList Resource
            org.hl7.fhir.dstu3.model.ListResource fihrConditionListObj = ConditionList.getConditionListResource();
            //referencing patient resource reference here
            fihrConditionListObj.setSubject(new Reference(patientResource));

            for (ConditionFull conditionFull : conditions) {
                String observationId = String.valueOf(conditionFull.getId());
                if (!observationIds.contains(observationId)) {
                    org.hl7.fhir.dstu3.model.Condition conditionFhirObj = Condition.getConditionResource(conditionFull, viewerDAL);
                    conditionFhirObj.getMeta().addTag(getMetaOrgCoding(conditionFull.getOrgId()+"",getOrgname(conditionFull.getOrgId(),viewerDAL)));

                    if (conditionFull.getEncounterId() != 0 && dbType.equalsIgnoreCase("ACUTE")) {
                        conditionFhirObj.setContext(new Reference(getEncounterFhirObj(conditionFull.getEncounterId(), viewerDAL, dbType)));
                    }
                    conditionFhirObj.setSubject(new Reference(patientResource));
                    fihrConditionListObj.addEntry().setItem(new Reference(conditionFhirObj));
                    bundle.addEntry().setResource(conditionFhirObj);
                }
            }
            bundle.addEntry().setResource(fihrConditionListObj);

        }
    }


    private void addFhirAllergiesToBundle(List<Long> patientIds, JDBCDAL viewerDAL) throws Exception {
        List<AllergyFull> allergies = viewerDAL.getAllergyFullList(patientIds);

        if (allergies.size() > 0) {
            LOG.info("Got Allergies");
            //create AllergiesList Resource
            org.hl7.fhir.dstu3.model.ListResource fihrAllergyListObj = AllergyList.getAllergyListResource();
            //referencing patient resource reference here
            fihrAllergyListObj.setSubject(new Reference(patientResource));
            for (AllergyFull allegyFull : allergies) {
                org.hl7.fhir.dstu3.model.AllergyIntolerance allergyFhirObj = AllergyIntolerance.getAllergyIntoleranceResource(allegyFull);
                allergyFhirObj.getMeta().addTag(getMetaOrgCoding(allegyFull.getOrganizationId()+"",getOrgname(allegyFull.getOrganizationId(),viewerDAL)));
                bundle.addEntry().setResource(allergyFhirObj);
                fihrAllergyListObj.addEntry().setItem(new Reference(allergyFhirObj));
            }
            bundle.addEntry().setResource(fihrAllergyListObj);

        }
    }

    private void addObservationToBundle(List<Long> patientIds, JDBCDAL viewerDAL, String dbtype, Map<String, Map<String, String>> valueSetCodeMap) throws Exception {
        // Observation resource
        Date baselineDate = FhirUtil.getBaselineDateForFilters(-1);
        List<ObservationFull> observationFullList = viewerDAL.getObservationFullList(patientIds, valueSetCodeMap);
        HashMap<String, Integer> vitalSignsCounter = new HashMap<>();
        if (observationFullList.size() > 0) {
            LOG.info("Got Observations");
            org.hl7.fhir.dstu3.model.ListResource observationListResource = ObservationList.getObservationResource();
            observationListResource.setSubject(new Reference(patientResource));
            String prevdate = null;
            String precode = null;
            int i = 0;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            if (!observationFullList.isEmpty()) {
                for (ObservationFull observationFull : observationFullList) {
                    //same code observations (vital-signs) restrict to max 3.
                    if (!FhirUtil.filterVitalSigns(observationFull, baselineDate, vitalSignsCounter)) {
                        continue;
                    }
                    //add observation id to processed observationList
                    observationIds.add(String.valueOf(observationFull.getId()));

                    //make observation date distinct in case of previous code,date and current code,date is same
                    if (null != prevdate && prevdate.equalsIgnoreCase(sdf.format(observationFull.getDate()))
                            && null != precode && precode.equalsIgnoreCase(observationFull.getCode())) {
                        i++;
                        observationFull.setDateStr(sdf.format(observationFull.getDate()) + FhirUtil.convertToTime(i));

                    } else {
                        observationFull.setDateStr(sdf.format(observationFull.getDate()) + " 00:00:00");
                        prevdate = sdf.format(observationFull.getDate());
                        precode = observationFull.getCode();
                        i = 0;
                    }
                    Observation observationFhir = new Observation(observationFull, viewerDAL);
                    org.hl7.fhir.dstu3.model.Observation observationResource = observationFhir.getObservationResource();
                    observationResource.getMeta().addTag(getMetaOrgCoding(observationFull.getOrganizationId()+"",getOrgname(observationFull.getOrganizationId(),viewerDAL)));
                    observationResource.setSubject(new Reference(patientResource));

                    if (observationFull.getEncounterId() != 0) {
                        observationResource.setContext(new Reference(getEncounterFhirObj(observationFull.getEncounterId(), viewerDAL, dbtype)));
                    }
                    bundle.addEntry().setResource(observationResource);
                    observationListResource.addEntry().setItem(new Reference(observationResource));

                }
            }
            bundle.addEntry().setResource(observationListResource);
        }
    }

    private void addFhirMedicationStatementToBundle(List<Long> patientIds, JDBCDAL viewerDAL) throws Exception {
        List<MedicationStatementFull> medicationStatementList = null;
        org.hl7.fhir.dstu3.model.MedicationStatement medicationStatementResource = null;
        org.hl7.fhir.dstu3.model.Medication medicationResource = null;

        medicationStatementList = viewerDAL.getMedicationStatementFullList(patientIds);
        if (medicationStatementList != null || medicationStatementList.size() > 0) {
            LOG.info("Got Medication Statements");
            org.hl7.fhir.dstu3.model.ListResource fhirMedicationStatementList = MedicationStatementList.getMedicationStatementListResource();
            fhirMedicationStatementList.setSubject(new Reference(patientResource));

            for (MedicationStatementFull medicationStatementFull : medicationStatementList) {
                medicationStatementResource = MedicationStatement.getMedicationStatementResource(medicationStatementFull);

                medicationStatementResource.getMeta().addTag(getMetaOrgCoding(medicationStatementFull.getOrganizationId()+"",getOrgname(medicationStatementFull.getOrganizationId(),viewerDAL)));

                medicationStatementResource.setSubject(new Reference(patientResource));

                if (medicationStatementFull.getPractitionerId() != 0) {
                    org.hl7.fhir.dstu3.model.Practitioner practitioner = getPractitionerResource(medicationStatementFull.getPractitionerId(), viewerDAL);
                    if (practitioner != null) {
                        medicationStatementResource.setInformationSource(new Reference(practitioner));
                    }
                }

                medicationResource = MedicationStatement.getMedicationResource(medicationStatementFull);
                medicationStatementResource.setMedication(new Reference(medicationResource));
                bundle.addEntry().setResource(medicationStatementResource);
                bundle.addEntry().setResource(medicationResource);
                fhirMedicationStatementList.addEntry().setItem(new Reference(medicationStatementResource));
            }
            bundle.addEntry().setResource(fhirMedicationStatementList);
        }
    }

    /*
       This method creates new organization resource for given organization id
       and add it to global organizationMap , if it is not available in global map
       and returns the organization resource from global organizationMap
       author :pp141
     */
    private Resource getOrganizationFhirObj(long organizationID, JDBCDAL viewerDAL) throws Exception {

        if (!organizationFhirMap.containsKey(organizationID)) {
            OrganizationFull patient_organization = viewerDAL.getOrganizationFull(organizationID);
            if(null == patient_organization)
            {
                return null;
            }
            organizationFhirMap.put(organizationID, Organization.getOrganizationResource(patient_organization));
        }
        return organizationFhirMap.get(organizationID);
    }

    private String getOrgname(long id, JDBCDAL viewerDAL) throws Exception {
        OrganizationFull organizationfull = viewerDAL.getOrganizationFull(id);

        return organizationfull.getOdsCode()+"__"+organizationfull.getName();
    }

    private void addToBundle(String bundlename) {
        if (bundlename.equalsIgnoreCase("organizations")) {
            Iterator iter = organizationFhirMap.keySet().iterator();
            while (iter.hasNext()) {
                bundle.addEntry().setResource((org.hl7.fhir.dstu3.model.Resource) organizationFhirMap.get(iter.next()));
            }
        }
        if (bundlename.equalsIgnoreCase("practitioners")) {
            if (!practitionerResourcesMap.isEmpty()) {

                Iterator iter = practitionerResourcesMap.keySet().iterator();
                while (iter.hasNext()) {

                    bundle.addEntry().setResource(practitionerResourcesMap.get(iter.next()));
                }
            }
        }
    }

    private void addFhirEncountersToBundle(List<Long> patientIds, JDBCDAL viewerDAL, String dbtype) throws Exception {
        List<EncounterFull> encounterFullList = viewerDAL.getEncounterFullList(patientIds, 0L, true, dbtype);
        if (encounterFullList.size() > 0) {
            LOG.info("Got Encounters");
            for (EncounterFull encounterFull : encounterFullList) {
                Long encounterID = new Long(encounterFull.getEncounterid());
                if (!encounterFhirMap.containsKey(encounterID)) {
                    org.hl7.fhir.dstu3.model.Encounter encounterObj = createEncounterFhirObject(encounterFull, viewerDAL,dbtype);

                    if (dbtype.equalsIgnoreCase("ACUTE")) {

                        if (encounterFull.getSp_organizationId() > 0) {
                            encounterObj.setServiceProvider(new Reference(getOrganizationFhirObj(encounterFull.getSp_organizationId(), viewerDAL)));
                        }


                        List<String> wardList= viewerDAL.getEncounterWardDetails(encounterFull.getEncounterid());
                        boolean flag=true;
                        String wardLocation="";
                        for(int i=0;i<wardList.size();i++)
                        {
                            if(wardList.get(i).contains("EPISODE_END_WARD_CODE"))
                            {
                                wardLocation=readWardLoc(wardList.get(i),"EPISODE_END_WARD_CODE");
                                flag=false;
                            }
                            else if(wardList.get(i).contains("DISCHARGE_WARD_CODE"))
                            {
                                wardLocation=readWardLoc(wardList.get(i),"DISCHARGE_WARD_CODE");
                                flag=false;
                            }

                            else if(flag)
                            {
                              if(wardList.get(i).contains("ADMISSION_WARD_CODE"))
                            {
                                wardLocation=readWardLoc(wardList.get(i),"ADMISSION_WARD_CODE");

                            }
                              else if(wardList.get(i).contains("EPISODE_START_WARD_CODE"))
                              {
                                  wardLocation=readWardLoc(wardList.get(i),"EPISODE_START_WARD_CODE");

                              }

                            }
                        }

                        encounterObj.addLocation().getLocation().setDisplay(wardLocation);

                        if(null !=encounterFull.getSpecialityMap().get("code"))
                        {
                            CodeableConcept code = new CodeableConcept();
                            code.addCoding()
                                    .setCode(encounterFull.getSpecialityMap().get("code"))
                                    .setDisplay(encounterFull.getSpecialityMap().get("value"));
                            code.setText(encounterFull.getSpecialityMap().get("value"));
                            List codes=  new ArrayList<CodeableConcept>();
                            codes.add(code);
                            encounterObj.setType(codes);
                        }

                    }
                   else {
                        String[] orgDetails = getOrgname(encounterFull.getOrganizationId(), viewerDAL).split("__");
                        if (orgDetails[1] != null){
                            encounterObj.addLocation().getLocation().setDisplay(orgDetails[1]);
                        }
                    }
                    encounterFhirMap.put(encounterID, encounterObj);
                    bundle.addEntry().setResource(encounterObj);
                }
            }
        }
    }

    private String readWardLoc(String jsonstr,String key) throws ParseException {
        if (jsonstr != null) {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonstr);
            return jsonObject.get(key).toString();
        }

        return null;
    }

    private Resource getEncounterFhirObj(Long encounterID, JDBCDAL viewerDAL, String dbtype) throws Exception {

        if (!encounterFhirMap.containsKey(encounterID)) {
            List<EncounterFull> encounterFullLis = viewerDAL.getEncounterFullList(Collections.emptyList(), encounterID, false, dbtype);
            if (encounterFullLis.size() < 1) {
                return null;
            }
            EncounterFull encounterFull = encounterFullLis.get(0);
            org.hl7.fhir.dstu3.model.Encounter encounterObj = createEncounterFhirObject(encounterFull, viewerDAL,dbtype);

            if (dbtype.equalsIgnoreCase("ACUTE")) {

                if (encounterFull.getSp_organizationId() > 0) {
                    encounterObj.setServiceProvider(new Reference(getOrganizationFhirObj(encounterFull.getSp_organizationId(), viewerDAL)));
                }

                List<String> wardList= viewerDAL.getEncounterWardDetails(encounterFull.getEncounterid());
                boolean flag=true;
                String wardLocation="";
                for(int i=0;i<wardList.size();i++)
                {
                    if(wardList.get(i).contains("EPISODE_END_WARD_CODE"))
                    {
                        wardLocation=readWardLoc(wardList.get(i),"EPISODE_END_WARD_CODE");
                        flag=false;
                    }
                    else if(wardList.get(i).contains("DISCHARGE_WARD_CODE"))
                    {
                        wardLocation=readWardLoc(wardList.get(i),"DISCHARGE_WARD_CODE");
                        flag=false;
                    }

                    else if(flag)
                    {
                        if(wardList.get(i).contains("ADMISSION_WARD_CODE"))
                        {
                            wardLocation=readWardLoc(wardList.get(i),"ADMISSION_WARD_CODE");

                        }
                        else if(wardList.get(i).contains("EPISODE_START_WARD_CODE"))
                        {
                            wardLocation=readWardLoc(wardList.get(i),"EPISODE_START_WARD_CODE");

                        }

                    }
                }

                encounterObj.addLocation().getLocation().setDisplay(wardLocation);

                if(null !=encounterFull.getSpecialityMap().get("code"))
                {
                    CodeableConcept code = new CodeableConcept();
                    code.addCoding()
                            .setCode(encounterFull.getSpecialityMap().get("code"))
                            .setDisplay(encounterFull.getSpecialityMap().get("value"));
                    code.setText(encounterFull.getSpecialityMap().get("value"));
                    List codes=  new ArrayList<CodeableConcept>();
                    codes.add(code);
                    encounterObj.setType(codes);
                }

            }
            else {
                String[] orgDetails = getOrgname(encounterFull.getOrganizationId(), viewerDAL).split("__");
                if (orgDetails[1] != null){
                    encounterObj.addLocation().getLocation().setDisplay(orgDetails[1]);
                }
            }

            encounterFhirMap.put(encounterID, encounterObj);
            bundle.addEntry().setResource(encounterObj);
        }
        return encounterFhirMap.get(encounterID);
    }

    private org.hl7.fhir.dstu3.model.Encounter createEncounterFhirObject(EncounterFull encounterFull, JDBCDAL viewerDAL,String dbType) throws Exception {
        org.hl7.fhir.dstu3.model.Encounter encounterObj = Encounter.getEncounterResource(encounterFull,dbType);
       // encounterObj.getMeta().addTag(patientCodingMap.get((encounterFull.getPatientId())));
        encounterObj.getMeta().addTag(getMetaOrgCoding(encounterFull.getOrganizationId()+"",getOrgname(encounterFull.getOrganizationId(),viewerDAL)));

        encounterObj.setSubject(new Reference(patientResource));
        if (encounterFull.getPractitionerId() != -1) {
            encounterObj.getParticipant().add(createEncounterParticipantComponent(encounterFull.getPractitionerId(), viewerDAL));
        }
        if(null !=encounterFull.getRefConsultant())
        {
            encounterObj.getParticipant().add(createRefConsultant(encounterFull.getRefConsultant()));
        }
        return encounterObj;
    }

    private org.hl7.fhir.dstu3.model.Encounter.EncounterParticipantComponent createEncounterParticipantComponent(long practitionerId, JDBCDAL viewerDAL) throws Exception {
        org.hl7.fhir.dstu3.model.Encounter.EncounterParticipantComponent epc = new org.hl7.fhir.dstu3.model.Encounter.EncounterParticipantComponent();
        List<CodeableConcept> coding = new ArrayList<>();


        CodeableConcept code = new CodeableConcept();
        code.addCoding()
                .setCode("PPRF")
                .setDisplay("primary performer")
                .setSystem("http://hl7.org/fhir/ValueSet/encounter-participant-type");
        coding.add(code);
        epc.setType(coding);

        org.hl7.fhir.dstu3.model.Practitioner practitioner = getPractitionerResource(practitionerId, viewerDAL);

        if (practitioner != null) {
            epc.setIndividual(new Reference(practitioner));
        }
        return epc;
    }

    private org.hl7.fhir.dstu3.model.Encounter.EncounterParticipantComponent createRefConsultant(String consultantName) throws Exception {
        org.hl7.fhir.dstu3.model.Encounter.EncounterParticipantComponent epc = new org.hl7.fhir.dstu3.model.Encounter.EncounterParticipantComponent();
        List<CodeableConcept> coding = new ArrayList<>();
        CodeableConcept code = new CodeableConcept();
        code.addCoding()
                .setCode("SRPF")
                .setDisplay("Referring Practitioner")
                .setSystem("http://hl7.org/fhir/ValueSet/encounter-participant-type");
        coding.add(code);
        epc.setType(coding);
        org.hl7.fhir.dstu3.model.Practitioner practitionerResource = null;
        practitionerResource = Practitioner.getPractitionerResource(consultantName);
        practitionerResourcesMap.put(practitionerResource.getId(), practitionerResource);
        if (practitionerResource != null) {
            epc.setIndividual(new Reference(practitionerResource));
        }
        return epc;
    }

    private org.hl7.fhir.dstu3.model.Practitioner createAppointmentRefConsultant(String consultantName) throws Exception {
        org.hl7.fhir.dstu3.model.Practitioner practitionerResource = null;
        practitionerResource = Practitioner.getPractitionerResource(consultantName);
        practitionerResourcesMap.put(practitionerResource.getId(), practitionerResource);
         return practitionerResource;
    }


    private void addProcedureToBundle(List<Long> patientIds, JDBCDAL viewerDAL) throws Exception {
        List<ProcedureFull> procedureFullList = viewerDAL.getProcedureFull(patientIds);

        if (!procedureFullList.isEmpty()) {

            for (ProcedureFull procedureFull : procedureFullList) {

                org.hl7.fhir.dstu3.model.Procedure procedureResource = Procedure.getProcedureResource(procedureFull);
               // procedureResource.getMeta().addTag(patientCodingMap.get((procedureFull.getPatientId())));
                procedureResource.getMeta().addTag(getMetaOrgCoding(procedureFull.getOrganizationId()+"",getOrgname(procedureFull.getOrganizationId(),viewerDAL)));


                org.hl7.fhir.dstu3.model.Practitioner practitioner = getPractitionerResource(procedureFull.getPractitionerId(), viewerDAL);
                if (practitioner != null) {
                    procedureResource.setPerformer(Arrays.asList(new org.hl7.fhir.dstu3.model.Procedure.ProcedurePerformerComponent(new Reference(practitioner))));
                }
                procedureResource.setSubject(new Reference(patientResource));
                bundle.addEntry().setResource(procedureResource);
            }
        }
    }

    private void addFhirImmunizationsToBundle(List<Long> patientIds, JDBCDAL viewerDAL, String dbtype) throws Exception {
        List<ImmunizationFull> immunizationfullList = viewerDAL.getImmunizationsFullList(patientIds);

        if (immunizationfullList.size() > 0) {
            LOG.info("Got Immunizations");

            for (ImmunizationFull immunizationFull : immunizationfullList) {
                org.hl7.fhir.dstu3.model.Immunization immunizationObj = Immunization.getImmunizationResource(immunizationFull);

                immunizationObj.getMeta().addTag(getMetaOrgCoding(immunizationFull.getOrgId()+"",getOrgname(immunizationFull.getOrgId(),viewerDAL)));

                immunizationObj.setPatient(new Reference(patientResource));
                if (immunizationFull.getEncounterID().trim().length() > 0)
                    immunizationObj.setEncounter(new Reference(getEncounterFhirObj(Long.parseLong(immunizationFull.getEncounterID()), viewerDAL, dbtype)));
                if (immunizationFull.getPractitionerID().trim().length() > 0) {
                    org.hl7.fhir.dstu3.model.Practitioner practitioner = getPractitionerResource(Long.parseLong(immunizationFull.getPractitionerID()), viewerDAL);
                    if (practitioner != null) {
                        immunizationObj.addPractitioner().setActor(new Reference(practitioner));
                    }
                }
                bundle.addEntry().setResource(immunizationObj);

            }
        }
    }

    private void addFhirAppointmentToBundle(List<Long> patientIds, JDBCDAL viewerDAL) throws Exception {
        List<AppointmentFull> appointmentList = null;
        org.hl7.fhir.dstu3.model.Appointment appointmentResource = null;
        org.hl7.fhir.dstu3.model.Slot slotResource = null;
        org.hl7.fhir.dstu3.model.Schedule scheduleResource = null;

        appointmentList = viewerDAL.getAppointmentFullList(patientIds);
        if (appointmentList != null || appointmentList.size() > 0) {
            Appointment fhirAppointment = new Appointment();
            Map<String, Schedule> scheduleIds = new HashMap<>();

            for (AppointmentFull appointmentFull : appointmentList) {
                appointmentResource = fhirAppointment.getAppointmentResource(appointmentFull);


                appointmentResource.addSupportingInformation(new Reference(getOrganizationFhirObj(appointmentFull.getSp_organizationId(), viewerDAL)));


                appointmentResource.getMeta().addTag(getMetaOrgCoding(appointmentFull.getOrgId()+"",getOrgname(appointmentFull.getOrgId(),viewerDAL)));

                String practStatus = appointmentFull.getStatus().toLowerCase().replaceAll(" ", "").equalsIgnoreCase("cancelled") ? "declined" : "accepted";

                appointmentResource.addParticipant().setActor(new Reference(patientResource))
                        .setStatus((new org.hl7.fhir.dstu3.model.Appointment.ParticipationStatusEnumFactory().fromCode(practStatus)));


                org.hl7.fhir.dstu3.model.Practitioner practitioner = getPractitionerResource(appointmentFull.getPractitionerId(), viewerDAL);
                if (practitioner != null) {
                    List<CodeableConcept> coding = new ArrayList<>();
                    CodeableConcept code = new CodeableConcept();
                    code.addCoding()
                    .setCode("PPRF")
                            .setDisplay("primary performer")
                            .setSystem("http://hl7.org/fhir/ValueSet/encounter-participant-type");
                    coding.add(code);
                      appointmentResource.addParticipant().setActor(new Reference(practitioner))
                            .setStatus((new org.hl7.fhir.dstu3.model.Appointment.ParticipationStatusEnumFactory().fromCode(practStatus)))
                            .setType(coding);
                }

                if(null!=appointmentFull.getRefConsultant())
                {
                    List<CodeableConcept> coding = new ArrayList<>();
                    CodeableConcept code = new CodeableConcept();
                    code.addCoding()
                            .setCode("SRPF")
                            .setDisplay("Referring Practitioner")
                            .setSystem("http://hl7.org/fhir/ValueSet/encounter-participant-type");
                    coding.add(code);
                    appointmentResource.addParticipant().setActor(new Reference(createAppointmentRefConsultant(appointmentFull.getRefConsultant())))
                    .setType(coding);

                }
                bundle.addEntry().setResource(appointmentResource);


            }
        }
    }

    private void addFhirFamilyMemberHistoryToBundle(List<Long> patientIds, JDBCDAL viewerDAL) throws Exception {
        List<FamilyMemberHistoryFull> familyMemberHistoryList = null;
        org.hl7.fhir.dstu3.model.FamilyMemberHistory familyMemberHistoryResource = null;

        familyMemberHistoryList = viewerDAL.getFamilyMemberHistoryFullList(patientIds);
        if (familyMemberHistoryList != null || familyMemberHistoryList.size() > 0) {
            LOG.info("Got Family member history");
            FamilyMemberHistory familyMemberHistory = new FamilyMemberHistory();

            for (FamilyMemberHistoryFull familyMemberHistoryFull : familyMemberHistoryList) {
                familyMemberHistoryResource = familyMemberHistory.getFamilyMemberHistoryResource(familyMemberHistoryFull);
                addPractitionerExt(familyMemberHistoryFull.getPractitionerId(), viewerDAL, familyMemberHistoryResource);

//                familyMemberHistoryResource.getMeta().addTag(patientCodingMap.get((familyMemberHistoryFull.getPatientId())));
                familyMemberHistoryResource.getMeta().addTag(getMetaOrgCoding(familyMemberHistoryFull.getOrganizationId(),getOrgname(Long.parseLong(familyMemberHistoryFull.getOrganizationId()),viewerDAL)));
                familyMemberHistoryResource.setPatient(new Reference(patientResource));

                bundle.addEntry().setResource(familyMemberHistoryResource);

            }
        }
    }

    private void addPractitionerExt(long practitionerId, JDBCDAL viewerDAL, org.hl7.fhir.dstu3.model.FamilyMemberHistory familyMemberHistoryResource) throws Exception {
        org.hl7.fhir.dstu3.model.Practitioner practitioner = getPractitionerResource(practitionerId, viewerDAL);
        Extension ext1 = new Extension();
        ext1.setUrl("https://fhir.hl7.org.uk/STU3/StructureDefinition/Extension-CareConnect-Recorder-1");
        ext1.setValue(new Reference(practitioner));
        familyMemberHistoryResource.addExtension(ext1);
    }

    private void addFhirReferralRequestsToBundle(List<Long> patientIds, JDBCDAL viewerDAL) throws Exception {
        List<ReferralRequestFull> referralRequestFullList = viewerDAL.getReferralRequestFullList(patientIds);

        if (referralRequestFullList.size() > 0) {
            LOG.info("Got Referral Requests");

            for (ReferralRequestFull referralRequestFull : referralRequestFullList) {
                org.hl7.fhir.dstu3.model.ReferralRequest referralRequest = ReferralRequest.getReferralRequestResource(referralRequestFull);
                    String orgName= getOrgname(Long.parseLong(referralRequestFull.getOrgId()),viewerDAL);
                List<Reference> recipients = new ArrayList<>();
                if(orgName.contains("MINT")) {
                    recipients.add(new Reference(getOrganizationFhirObj(Long.parseLong(referralRequestFull.getOrgId()), viewerDAL)));
                    referralRequest.setRecipient(recipients);
                } else {
                    referralRequest.getMeta().addTag(getMetaOrgCoding(referralRequestFull.getOrgId(), orgName));
                }

                referralRequest.setSubject(new Reference(patientResource));
                referralRequest.addIdentifier()
                        .setValue(String.valueOf(referralRequestFull.getId()))
                        .setSystem(ResourceConstants.SYSTEM_ID);
                long refId = Long.parseLong(referralRequestFull.getId());


                if (referralRequestFull.getRecipientOrganizationId() != null) {
                    recipients.add(new Reference(getOrganizationFhirObj(Long.parseLong(referralRequestFull.getRecipientOrganizationId()), viewerDAL)));
                    referralRequest.setRecipient(recipients);
                } else {

                    String recOrgRaw= viewerDAL.recipientOrg(refId);
                    if (recOrgRaw!=null && !recOrgRaw.isEmpty()) {
                        int colonIndex = recOrgRaw.indexOf(':');

                        if (colonIndex != -1 && colonIndex < recOrgRaw.length() - 1) {
                            String recOrg = recOrgRaw.substring(colonIndex + 1).trim();
                            Long recepOrg= viewerDAL.getOrgId(recOrg);

                            recipients.add(new Reference(getOrganizationFhirObj(recepOrg, viewerDAL)));
                            referralRequest.setRecipient(recipients);
                        }
                    }
                }

                String team= viewerDAL.serviceOffered(refId);
                CodeableConcept codeableConcept = new CodeableConcept();
                if(orgName.contains("MINT")) {
                     if(referralRequestFull.getEndDate()!=null && team!=null) {
                             LocalDate date = LocalDate.parse(referralRequestFull.getEndDate());
                             DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                             String endDate = date.format(formatter);

                             team= "Closed " + endDate  + " ("+team+")";
                         }
                  }
                referralRequest.setServiceRequested(Arrays.asList(codeableConcept.setText(team)));

                if (referralRequestFull.getPractitionerId() != null) {

                    org.hl7.fhir.dstu3.model.Practitioner practitioner = getPractitionerResource(Long.parseLong(referralRequestFull.getPractitionerId()), viewerDAL);
                    if (practitioner != null) {
                        referralRequest.setRequester(new org.hl7.fhir.dstu3.model.ReferralRequest.ReferralRequestRequesterComponent(new Reference(practitioner)));
                    }
                }

                bundle.addEntry().setResource(referralRequest);
            }
        }
    }

    private void setCoding(Map<Long, String> patientMap) {
        patientMap.entrySet().stream().forEach(e -> {
            Long patientId = e.getKey();
            if (patientMap.containsKey(patientId)) {
                String[] values = patientMap.get(patientId).split("#");
                Coding coding = new Coding();
                coding.setCode(values[0]);
                coding.setDisplay(values[1]);
                coding.setSystem(ResourceConstants.ORGANIZATION_CODE);
                patientCodingMap.put(patientId, coding);
            }
        });
    }


    private Coding getMetaOrgCoding(String code , String value) {

        String[] orgDetails= value.split("__");
                Coding coding = new Coding();
                coding.setCode(orgDetails[0]);
                coding.setDisplay(orgDetails[1]);
                coding.setSystem(ResourceConstants.ORGANIZATION_CODE);
                return coding;
            }


    private JSONObject parseBundle() throws ParseException {
        LOG.info("Resource Fetching complete. Parsing Bundle to JSON.");
        FhirContext ctx = FhirContext.forDstu3();
        String encodedBundle = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
        JSONParser parser = new JSONParser();
        return (JSONObject) parser.parse(encodedBundle);
    }

}
