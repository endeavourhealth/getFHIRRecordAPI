package org.endeavourhealth.getFHIRRecordAPI.common.dal;

import org.apache.commons.lang3.StringUtils;
import org.endeavourhealth.core.database.rdbms.enterprise.EnterpriseConnector;
import org.endeavourhealth.getFHIRRecordAPI.common.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static org.endeavourhealth.getFHIRRecordAPI.common.dal.QueryConstants.getAppointmentSpecialityQuery;
import static org.endeavourhealth.getFHIRRecordAPI.common.dal.QueryConstants.getEncounterSpecialityQuery;

public class JDBCDAL implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(JDBCDAL.class);
    private static final String CONFIG_GP = "GP";
    private static final String CONFIG_ACUTE="ACUTE";
    private static final String CONFIG_COMM = "COMMUNITY";
    List<String> validOrgs = new ArrayList<>();
    Connection connection = null;

    public void setValidOrgs(List<String> orgs) {
        validOrgs = orgs;
    }

    public void setConnectionAndOrgs(List<String> orgs,String configName) throws Exception {
        validOrgs = orgs;
        setSubscriberConnection(configName);
    }

    public void closeSubscriberConnection() throws Exception {
        this.connection.close();
    }
      private  String getCodeURL(String concept_id) {
        if(null != concept_id)
        {
            if(concept_id.startsWith("SN_"))
            {
                return "http://snomed.info/sct";
            }
            else if(concept_id.startsWith("R2_"))
            {
                return "http://read.info/readv2";
            }
            else if(concept_id.startsWith("R3_"))
            {
                return "http://read.info/ctv3";
            }
            else if(concept_id.startsWith("EMLOC_"))
            {
                return "http://emis.info/emis_local";
            }
            else if(concept_id.startsWith("O4_"))
            {
                return "http://opcs.info/opcs4";
            }
            else if(concept_id.startsWith("I10_"))
            {
                return "http://icd10.info/icd10";
            }
            else if(concept_id.startsWith("TPPLOC_"))
            {
                return "http://tpp.info/tpp_local";
            }
            else if(concept_id.startsWith("BC_"))
            {
                return "http://barts.info/barts_cerner";
            }
        }
        return "http://snomed.info/sct";


    }

    public void setSubscriberConnection(String configName) throws Exception {
        LOG.info("Getting connection for" + configName);
        List<EnterpriseConnector.ConnectionWrapper> connectionWrappers = EnterpriseConnector.openSubscriberConnections(configName);

        for (EnterpriseConnector.ConnectionWrapper wrapper: connectionWrappers) {

            if (wrapper.hasDatabaseConnection()) {

                connection = wrapper.getConnection();
                LOG.info("Connect established");
                break;
            }
        }

        if (connection == null) {
            LOG.info("Connection has not been found");
            throw new Exception("Connection has not been found");
        }

    }

    public PatientFull getPatientFull( String nhsNumber, String dateOfBirth, boolean includeOnlyActivePatient, boolean includeOrgFilter,String dbtype) throws Exception {
        if(dbtype.equalsIgnoreCase(CONFIG_ACUTE)) includeOnlyActivePatient=true;

        String sql = "SELECT p.id,"+
                "coalesce(p.organization_id,'') as orglocation,"+
                "coalesce(o.name,'') as orgname,"+
                "p.date_of_birth as dob,"+
                "p.date_of_death as dod,"+
                "coalesce(c.description, c.name, '') as gender,"+
                "coalesce(p.nhs_number,'') as nhsNumber,"+
                "coalesce(p.last_name,'') as lastname,"+
                "coalesce(p.first_names,'') as firstname,"+
                "coalesce(p.title,'') as title,"+
                "coalesce(a.address_line_1,'') as add1,"+
                "coalesce(a.address_line_2,'') as add2,"+
                "coalesce(a.address_line_3,'') as add3,"+
                "coalesce(a.address_line_4,'') as add4,"+
                "coalesce(a.city,'') as city,"+
                "coalesce(e.usual_gp_practitioner_id,'') as practitionerId,"+
                "coalesce(a.postcode,'') as postcode," +
                "coalesce(c3.description, '') as ethnicDescription," +
                "coalesce(c2.description, '') as registrationStatusValue," +
                "coalesce(e.registration_type_concept_id,'') as registrationType,"+
                "e.date_registered_end as registeredEndDate,"+
                "e.date_registered as startdate,"+
                "'HOME' as adduse,"+
                "'' as otheraddresses, "+
                " o.ods_code as ods_code "+
                "FROM patient p " +
                "join organization o on o.id = p.organization_id " +
                "left join patient_address a on a.id = p.current_address_id " +
                "left join concept c on c.dbid = p.gender_concept_id " +
                "left join episode_of_care e on e.patient_id = p.id "+
                "left join concept c3 on c3.dbid = p.ethnic_code_concept_id "+
                "left join concept c2 on c2.dbid = e.registration_type_concept_id ";
                if(dateOfBirth.equalsIgnoreCase("0")) {
                    sql = sql.concat("where p.nhs_number = ? ");
                }
                else
                {
                    sql = sql.concat("where p.nhs_number = ? and p.date_of_birth = ? ");
                }

            if (includeOnlyActivePatient) {
                sql = sql.concat("and p.date_of_death IS NULL ");
                if(dbtype.equalsIgnoreCase(CONFIG_COMM) ) {
                    sql = sql + " and (e.date_registered IS NULL or e.date_registered <= DATE(NOW()))";
                }
                else if(dbtype.equalsIgnoreCase(CONFIG_GP)){
                    sql = sql + " and (e.date_registered IS NULL or e.date_registered <= DATE(NOW())" +
                            " and (e.date_registered_end >= DATE(NOW()) or e.date_registered_end IS NULL))";
                  sql = sql.concat(" and (c2.code IS NULL or c2.code = 'R') ");
                }
            }

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nhsNumber);
            if(!dateOfBirth.equalsIgnoreCase("0"))
                statement.setString(2, (dateOfBirth.equals("0") ? null : dateOfBirth));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    if (includeOrgFilter) {
                        String odsCode = resultSet.getString("ods_code");
                        if (!validOrgs.contains(odsCode)) {
                            return null;
                        }
                    }
                    return getPatientFull(resultSet);
                }
            }
        }

        return null;
    }


    public Map<Long, String> getPatientIds(String nhsNumber, boolean includeOrgFilter) throws Exception {
        Map<Long, String> results = new HashMap();
        String sql = "SELECT p.id, " +
                "o.ods_code as code, " +
                "o.name as display " +
                "from patient p join organization o on p.organization_id = o.id where " +
                " p.nhs_number = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nhsNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {

                    if (includeOrgFilter) {
                        String odsCode = resultSet.getString("code");
                        if (!validOrgs.contains(odsCode)) {
                            continue;
                        }
                    }
                    results.put(resultSet.getLong("id"), StringUtils.join(
                            resultSet.getString("code"), "#", resultSet.getString("display")));
                }
            }
        }
        return results;
    }

    public List<EpisodeOfCareFull> getEpisodeOfCareFull(List<Long> patientIds) throws Exception {
        List<EpisodeOfCareFull> episodeOfCareFullResult = new ArrayList<>();;

        String sql = "SELECT coalesce(e.date_registered, '') as dateRegistered," +
                "coalesce(e.id, '') as id, " +
                "coalesce(e.patient_id, '') as patientId, " +
                "coalesce(e.date_registered_end, '') as dateRegisteredEnd, " +
                "coalesce(e.organization_id, '') as organizationId, " +
                "coalesce(e.usual_gp_practitioner_id, '') as practitionerId, " +
                "coalesce(co.code, '') as code," +
                "coalesce(co.name, '') as name " +
                "FROM episode_of_care e " +
                "left join concept co on e.registration_type_concept_id = co.dbid " +
                "where e.patient_id in (" + StringUtils.join(patientIds, ',') + ") " + "order by e.organization_id";


        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    episodeOfCareFullResult.add(getEpisodeOfCareFull(resultSet));
            }
        }


        return episodeOfCareFullResult;
    }

    private EpisodeOfCareFull getEpisodeOfCareFull(ResultSet resultSet) throws Exception {
        EpisodeOfCareFull episodeOfCareFull = new EpisodeOfCareFull();

        episodeOfCareFull.setCode(resultSet.getString("code"))
                .setId(resultSet.getLong("id"))
                .setPatientId(resultSet.getLong("patientId"))
                .setOrganizationId(resultSet.getLong("organizationId"))
                .setPractitionerId(resultSet.getLong("practitionerId"))
                .setDateRegistered(resultSet.getString("dateRegistered"))
                .setDateRegisteredEnd(resultSet.getString("dateRegisteredEnd"))
                .setName(resultSet.getString("name"));

        return episodeOfCareFull;
    }

    public String getDescriptionFromObservation(Long id) throws Exception{
        String description = null;
        String sql = "select valueConcept.description as description from observation_additional oa " +
               " join concept propConcept on propConcept.dbid = oa.property_id " +
                "join concept valueConcept on valueConcept.dbid = oa.value_id " +
                "where propConcept.id = 'CM_ProblemSignificance' and oa.id=" + id;


        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    description = String.valueOf(resultSet.getString("description").replace(" (qualifier value)", ""));
            }
        }

        return description;
    }


    public String getJsonValueFromObservationAdditional(Long id) throws Exception{
        String jsonString = null;
        String sql = "select oa.json_value from observation_additional oa " +
                " join concept propConcept on propConcept.dbid = oa.property_id " +
                " where propConcept.id = 'CM_ResultReferenceRange' and oa.id=" + id;


        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    jsonString = String.valueOf(resultSet.getString("json_value"));
            }
        }

        return jsonString;
    }


    public List<ObservationFull> getObservationFullList(List<Long> id,Map<String, Map<String, String>> valueSetCodeMap) throws Exception {
        List<ObservationFull> observationFullList = new ArrayList<>();

        String sql = "SELECT o.id as id," +
                "DATE(o.clinical_effective_date) as date, "+
                "coalesce(o.patient_id, '') as patientId, " +
                "coalesce(o.practitioner_id, '') as practitionerId, " +
                "coalesce(o.encounter_id, '') as encounterId, " +
                "coalesce(o.organization_id, '') as organizationId, " +
                "coalesce(o.result_value, '') as resultValue, " +
                "coalesce(c.code,'') as code," +
                "coalesce(c.name, '') as name, " +
                "coalesce(c.description, c.name, '') as description," +
                "coalesce(v.id, '') as category," +
                "coalesce(o.result_value_units,'') as resultValueUnits from  observation o " +
                "join value_set_member vsm on vsm.concept = o.non_core_concept_id " +
                "join concept c on c.dbid = o.non_core_concept_id " +
                "join  value_set v ON v.dbid = vsm.value_set and v.id = 'http://endhealth.info/im#VSET_VitalSigns' "+
                "where o.is_problem = 0 and o.clinical_effective_date is not null and o.patient_id in (" + StringUtils.join(id, ',') + ") "+
                "order by date desc,code,o.id desc";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    observationFullList.add(getObservationFull(resultSet, valueSetCodeMap));
            }
        }

        return observationFullList;
    }


    public List<DiagnosticReportFull> getDiagnosticReportFullList(List<Long> id) throws Exception {
        List<DiagnosticReportFull> diagnosticReportFullList = new ArrayList<>();
        //Pathology specimen removed
        String sql = "SELECT o.id as id," +
                "o.clinical_effective_date as date," +
                "coalesce(o.patient_id, '') as patientId, " +
                "coalesce(o.practitioner_id, '') as practitionerId, " +
                "coalesce(o.encounter_id, '') as encounterId, " +
                "coalesce(o.organization_id, '') as organizationId, " +
                "coalesce(o.result_value, '') as resultValue, " +
                "coalesce(c.code,'') as code," +
                "coalesce(c.name, '') as name, " +
                "coalesce(c.description, c.name, '') as description," +
                "coalesce(o.result_value_units,'') as resultValueUnits, "+
                "coalesce(v.name, '') as category from observation o " +
                "join concept c on o.non_core_concept_id = c.dbid " +
                "left outer join value_set_member vsm on vsm.concept = o.non_core_concept_id  " +
                "left outer join value_set v ON v.dbid = vsm.value_set "+
                " where  o.patient_id in (" + StringUtils.join(id, ',') + ") " +
                "and (o.result_value_units is not null or o.result_value is not null or o.result_date is not null or o.result_text is not null or o.result_concept_id is not null) and " +
                " v.id in ('http://endhealth.info/im#VSET_Microbiology','http://endhealth.info/im#VSET_Radiology','http://endhealth.info/im#VSET_VitalSigns') ";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    diagnosticReportFullList.add(getDiagnosticReportFull(resultSet));
            }
        }

        return diagnosticReportFullList;
    }

    public PractitionerFull getPractitionerFull(long practitionerId) throws Exception {
        PractitionerFull result = null;

        String sql = "select id, name,role_code,role_Desc from practitioner pr where id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, practitionerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    result = (getPractitionerFull(resultSet));
            }
        }

        return result;
    }

    public List<ProcedureFull> getProcedureFull(List<Long> patientIds) throws Exception {
        List<ProcedureFull> procedureList = new ArrayList<>();

        String sql = "SELECT coalesce(o.clinical_effective_date, '') as date," +
                "coalesce(o.id, '') as id," +
                "coalesce(o.patient_id, '') as patientId," +
                "coalesce(o.practitioner_id, '') as practitionerId," +
                "coalesce(o.organization_id, '') as organizationId," +
                "CASE WHEN o.problem_end_date IS NULL THEN 'Active'\n" +
                "ELSE 'Past' END as status,coalesce(c.description, c.name, '') as name, c.code\n" +
                "FROM observation o\n" +
                "join concept c on c.dbid = o.non_core_concept_id\n" +
                "left outer join value_set_member vsm on vsm.concept = o.non_core_concept_id  " +
                "left outer join value_set v ON v.dbid = vsm.value_set "+
                "where patient_id in (" + StringUtils.join(patientIds, ',') + ") " + "and v.id in ('http://endhealth.info/im#VSET_Category_Procedures','http://endhealth.info/im#VSET_Radiology') order by o.clinical_effective_date DESC";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    procedureList.add(getProcedure(resultSet));
            }
        }

        return procedureList;
    }

    public List<TelecomFull> getTelecomFull(long patientId) throws Exception {
        List<TelecomFull> telecomFullList = new ArrayList<>();

        String sql = "select ctype.description as description1, cuse.description as description2, pc.value as value, pc.patient_id as id from patient_contact pc " +
        "join concept cuse on cuse.dbid = pc.use_concept_id " +
        "join concept ctype on ctype.dbid = pc.type_concept_id " +
                "where patient_id = " + patientId;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    telecomFullList.add(getTelecom(resultSet));
            }
        }

        return telecomFullList;
    }

    public List<String> getEncounterWardDetails(long encounter_Id) throws Exception {
        List<String> wardList = new ArrayList<String>();

        String sql = "SELECT id,json_value FROM encounter_additional where  json_value like '%_WARD_CODE%' " +
                " and id  in ( select id from encounter_event where encounter_id="+encounter_Id+")";


        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    wardList.add(resultSet.getString("json_value"));
                }


            }
        }

        return wardList;
    }

    public String[] getReligion(long patientId) throws Exception {
        String[] religionData = new String[2];

        String sql = "select " +
                "coalesce(c.description, c.name, '') as name, c.code as code from observation o " +
                "join concept c on o.non_core_concept_id = c.dbid " +
                " join value_set_member vsm on vsm.concept = o.non_core_concept_id "+
                " join  value_set v ON v.dbid = vsm.value_set and v.id = 'http://endhealth.info/im#VSET_Religion' "+
                "where patient_id = " + patientId;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {

                if (resultSet.next()) {
                    return getDemographicsData(resultSet, religionData);
                } else {
                    return null;
                }
            }
        }
    }

    public String[] getLanguage(long patientId) throws Exception {
        String[] language = new String[2];

        String sql = "select " +
                "coalesce(c.description, c.name, '') as name, c.code as code from observation o " +
                "join concept c on o.non_core_concept_id = c.dbid " +
                " join value_set_member vsm on vsm.concept = o.non_core_concept_id "+
                " join  value_set v ON v.dbid = vsm.value_set and v.id = 'http://endhealth.info/im#VSET_Language' "+
                "where patient_id = " + patientId;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {

                if (resultSet.next()) {
                    return getDemographicsData(resultSet, language);
                } else {
                    return null;
                }
            }
        }
    }

    private String[] getDemographicsData(ResultSet resultSet, String[] data) throws SQLException {
        data[0] = (resultSet.getString("code"));
        data[1] =  resultSet.getString("name");
        return data;
    }

    private TelecomFull getTelecom(ResultSet resultSet) throws SQLException {
        TelecomFull telecomFull = new TelecomFull();

        telecomFull.setDescription1(resultSet.getString("description1"))
                .setDescription2(resultSet.getString("description2"))
                .setValue(resultSet.getString("value"))
                .setId(resultSet.getString("id"));
        return telecomFull;
    }


    private ProcedureFull getProcedure(ResultSet resultSet) throws SQLException {
        ProcedureFull procedureFull = new ProcedureFull();

        procedureFull.setDate(resultSet.getDate("date"))
                .setPatientId(resultSet.getLong("patientId"))
                .setStatus(resultSet.getString("status"))
                .setName(resultSet.getString("name"))
                .setId(resultSet.getString("id"))
                .setPractitionerId(resultSet.getLong("practitionerId"))
                .setOrganizationId(resultSet.getLong("organizationId"))
                .setCode(resultSet.getString("code"));
        return procedureFull;
    }

    public LocationFull getLocation(long organizationId) throws Exception {
        LocationFull locationFull = new LocationFull();

        String sql = "SELECT l.id as id, coalesce(l.name, '') as name, " +
                "coalesce(l.type_code, '') as code, " +
                "coalesce(l.type_desc,'') as description, " +
                "coalesce(l.postcode, '') as postcode " +
                "from location l " +
                "where l.managing_organization_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, organizationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    locationFull = getLocation(resultSet);
            }
        }
        return locationFull;
    }

    private LocationFull getLocation(ResultSet resultSet) throws SQLException {
        LocationFull locationFull = new LocationFull();

        locationFull.setId(resultSet.getLong("id"))
                .setName(resultSet.getString("name"))
                .setCode(resultSet.getString("code"))
                .setDesc(resultSet.getString("description"))
                .setPostCode(resultSet.getString("postCode"));

        return locationFull;
    }

    public ObservationFull getObservationFull(ResultSet resultSet,Map<String, Map<String, String>> valueSetCodeMap) throws SQLException {
        ObservationFull observationFull = new ObservationFull();

        observationFull.setId(resultSet.getLong("id"))
                .setPatientId(resultSet.getLong("patientId"))
                .setCode(resultSet.getString("code"))
                .setDate(resultSet.getDate("date"))
                .setDescription(resultSet.getString("description"))
                .setPractitionerId(resultSet.getLong("practitionerId"))
                .setOrganizationId(resultSet.getLong("organizationId"))
                .setEncounterId(resultSet.getLong("encounterId"))
                .setName(resultSet.getString("name"))
                .setResultValue(resultSet.getDouble("resultValue"))
                .setCategory(resultSet.getString("category"))
                .setResultValueUnits(resultSet.getString("resultValueUnits"));
        if(null!=observationFull.getCategory() && null!=valueSetCodeMap)
        {
            observationFull.setCategoryCode(valueSetCodeMap.get(observationFull.getCategory()).get("code"));
            observationFull.setCategoryText(valueSetCodeMap.get(observationFull.getCategory()).get("text"));
        }
        return observationFull;
    }

    public DiagnosticReportFull getDiagnosticReportFull(ResultSet resultSet) throws SQLException {
        DiagnosticReportFull diagnosticReportFull = new DiagnosticReportFull();

        diagnosticReportFull.setId(resultSet.getLong("id"))
                .setPatientId(resultSet.getLong("patientId"))
                .setCode(resultSet.getString("code"))
                .setDate(resultSet.getString("date"))
                .setDescription(resultSet.getString("description"))
                .setPractitionerId(resultSet.getLong("practitionerId"))
                .setOrganizationId(resultSet.getLong("organizationId"))
                .setEncounterId(resultSet.getLong("encounterId"))
                .setName(resultSet.getString("name"))
                .setResultValue(resultSet.getDouble("resultValue"))
                .setCategory(resultSet.getString("category"))
                .setResultValueUnits(resultSet.getString("resultValueUnits"));
        return diagnosticReportFull;
    }

    public  PatientFull getPatientFull(ResultSet resultSet) throws Exception {
        PatientFull patient = new PatientFull();

        patient
                .setId(resultSet.getString("id"))
                .setNhsNumber(resultSet.getString("nhsNumber"))
                .setGender(resultSet.getString("gender"))
                .setLastname(resultSet.getString("lastname"))
                .setTitle(resultSet.getString("title"))
                .setFirstname(resultSet.getString("firstname"))
                .setDob(resultSet.getDate("dob"))
                .setDod(resultSet.getDate("dod"))
                .setAdduse(resultSet.getString("adduse"))
                .setAdd1(resultSet.getString("add1"))
                .setAdd2(resultSet.getString("add2"))
                .setAdd3(resultSet.getString("add3"))
                .setAdd4(resultSet.getString("add4"))
                .setPostcode(resultSet.getString("postcode"))
                .setCity(resultSet.getString("city"))
                .setOtheraddresses(resultSet.getString("otheraddresses"))
                .setOrglocation(resultSet.getString("orglocation"))
                .setOrgname(resultSet.getString("orgname"))
                .setPractitionerId(resultSet.getLong("practitionerId"))
                .setRegistrationEndDate(resultSet.getDate("registeredEndDate"))
                .setRegistrationType(resultSet.getString("registrationType"))
                .setStartdate(resultSet.getDate("startdate"))
                .setRegistrationStatusValue(resultSet.getString("registrationStatusValue"))
                .setEthnicDescription(resultSet.getString("ethnicDescription"));

        patient.setTelecomFullList(getTelecomFull(Long.parseLong(patient.getId())));
        return patient;
    }

    private PractitionerFull getPractitionerFull(ResultSet resultSet) throws SQLException {
        PractitionerFull practitionerFull = new PractitionerFull();

        practitionerFull.setId(resultSet.getString("id"))
                .setName(resultSet.getString("name"))
                .setRoleCode(resultSet.getString("role_code"))
                .setRoleDesc(resultSet.getString("role_Desc"));

        return practitionerFull;
    }

    public List<AllergyFull> getAllergyFullList(List<Long> patientids) throws Exception {
        ArrayList<AllergyFull> allergiesFullList =new ArrayList<AllergyFull>();
        String sql = " SELECT a.id as id, a.patient_id as patientId, a.clinical_effective_date as date, " +
                " coalesce(c.description, c.name, '') as name ,c.code,c.id concept_id ,a.organization_id,a.practitioner_id " +
            " FROM allergy_intolerance a " +
                " join concept c on c.dbid = a.non_core_concept_id " +
                " join value_set_member vsm on vsm.concept = a.non_core_concept_id "+
                " join  value_set v ON v.dbid = vsm.value_set " +
                " where v.id = 'http://endhealth.info/im#VSET_Allergy'" +
                " and a.clinical_effective_date is not null and a.patient_id in (" + StringUtils.join(patientids, ',') + ") ";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                allergiesFullList = getAllergyFullList(resultSet,allergiesFullList);
            }
        }
        allergiesFullList= getAllergyFullListFromObservation( patientids, allergiesFullList);

        return allergiesFullList;
    }

    /* NWL want pathology and radiology data from GP source systems removed from the extract. Once we get HL7 data in
        we need to ensure that we are sending the data from hospitals.  This will be identified by having an entry in
        observation_additional with the requesting GP organisation specified
    */
    public Set<String> getPathologyAndRadiologyList(List<Long> patientIds) throws Exception {

        Set<String> pathAndRadObservationIds = new HashSet<>();
        String sql = "SELECT " +
                "coalesce(o.id, '') as id " +
                "FROM observation o " +
                " join value_set_member vsm on vsm.concept = o.non_core_concept_id "+
                " join  value_set v ON v.dbid = vsm.value_set " +
                "where patient_id in (" + StringUtils.join(patientIds, ',') + ") " + " and  v.id in ('http://endhealth.info/im#VSET_Radiology') ";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    pathAndRadObservationIds.add(resultSet.getString("id"));
            }
        }
        return pathAndRadObservationIds;
    }

    private ArrayList<AllergyFull> getAllergyFullListFromObservation(List<Long> patientids,ArrayList<AllergyFull> allergylist) throws Exception {

        String sql = "select o.id as id, o.patient_id as patientId, o.clinical_effective_date as date, " +
                " coalesce(c.description, c.name, '') as name ,c.code,c.id concept_id ,o.organization_id,o.practitioner_id from observation o " +
                "join value_set_member vsm on vsm.concept = o.non_core_concept_id " +
                "join concept c on c.dbid = o.non_core_concept_id " +
                 " join  value_set v ON v.dbid = vsm.value_set " +
                "where v.id = 'http://endhealth.info/im#VSET_Allergy'" +
                " and o.patient_id in  (" + StringUtils.join(patientids, ',') + ")";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                allergylist = getAllergyFullList(resultSet,allergylist);
            }
        }
        return allergylist;
    }

    public OrganizationFull getOrganizationFull(long organizationId) throws Exception {

        String sql = "select id as id," +
                     "coalesce(ods_code,'') as ods_code," +
                     "coalesce(name,'') as name," +
                     "coalesce(postcode,'') as postcode  from organization where id= ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, organizationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next())
                    return getOrganizationFull(resultSet);
            }
        }

        return null;
    }

    public  OrganizationFull getOrganizationFull(ResultSet resultSet) throws SQLException {
        OrganizationFull organizationFull = new OrganizationFull();
        organizationFull
                .setId(resultSet.getLong("id"))
                .setOdsCode(resultSet.getString("ods_code"))
                .setName(resultSet.getString("name"))
                .setPostCode(resultSet.getString("postcode"));

        return organizationFull;
    }

    public ArrayList<AllergyFull> getAllergyFullList(ResultSet resultSet,ArrayList<AllergyFull> allergylist) throws SQLException {
           if(null !=resultSet) {
            while (resultSet.next()) {
                AllergyFull allergyDtls = new AllergyFull();
                allergyDtls
                        .setId(resultSet.getLong("id"))
                        .setPatientId(resultSet.getLong("patientId"))
                        .setDate(resultSet.getDate("date"))
                        .setName(resultSet.getString("name"))
                        .setCode(resultSet.getString("code"))
                        .setOrganizationId(resultSet.getLong("organization_id"))
                        .setPractitionerId(resultSet.getLong("practitioner_id"))
                        .setCodeURL(getCodeURL(resultSet.getString("concept_id")));

                allergylist.add(allergyDtls);
            }
        }
        return allergylist;
    }


    /**
     *
     * @param patientIds
     * @return
     * @throws Exception
     */
    public List<MedicationStatementFull> getMedicationStatementFullList(List<Long> patientIds) throws Exception {
        List<MedicationStatementFull> result = null;
        String sql = "select ms.id as msid, coalesce(c.description, c.name, '') as name, c.code,c.id concept_id ,  " +
                "coalesce(ms.clinical_effective_date,'') as clinicalEffDt, " +
                "coalesce(ms.patient_id,'') as patientId, " +
                "coalesce(ms.dose,'') as dose, " +
                "coalesce(ms.quantity_value,'') as qValue, " +
                "coalesce(ms.cancellation_date,'') as cancellation_date, " +
                "coalesce(ms.quantity_unit,'') as qUnit, \n" +
                "max(coalesce(mo.clinical_effective_date,'')) as valueDtTime, " +
                "ms.authorisation_type_concept_id as atCid, " +
                "coalesce(ms.practitioner_id, 0) as practitionerId, " +
                "coalesce(ms.organization_id, 0) as organizationId, " +
                "max(coalesce(mo.clinical_effective_date,'')) as last_issue_date " +
                "from medication_statement ms join medication_order mo on ms.id=mo.medication_statement_id and ms.patient_id = mo.patient_id " +
                "join concept c on c.dbid=ms.non_core_concept_id " +
                //"where (ms.cancellation_date is null or (ms.cancellation_date is not null and ms.clinical_effective_date >= date_add(now(), interval -1 year))) " +
                "where (ms.cancellation_date is null or ms.cancellation_date >= date_add(now(), interval -1 year)) " +
                "and ms.patient_id in (" + StringUtils.join(patientIds, ',') + ") and ms.clinical_effective_date is not null "  +  "group by msid";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                result = getMedicationStatementFullList(resultSet);
            }
        }
        return result;
    }

    /**
     *
     * @param resultSet
     * @return
     * @throws SQLException
     */
    public  List<MedicationStatementFull> getMedicationStatementFullList(ResultSet resultSet) throws SQLException {
        List<MedicationStatementFull> medicationStatementList = new ArrayList<MedicationStatementFull>();
        while (resultSet.next()) {
            MedicationStatementFull medicationStatement = new MedicationStatementFull();
            medicationStatement
                    .setId(resultSet.getLong("msid"))
                    .setPatientId(resultSet.getLong("patientId"))
                    .setName(resultSet.getString("name"))
                    .setCode(resultSet.getString("code"))
                    .setDate(resultSet.getString("clinicalEffDt"))
                    .setDose(resultSet.getString("dose"))
                    .setCancellationDate(resultSet.getString("cancellation_date"))
                    .setValueDateTime(resultSet.getString("valueDtTime"))
                    .setLastIssueDate(resultSet.getString("last_issue_date"))
                    .setOrganizationId(resultSet.getLong("organizationId"))
                    .setCodeURL(getCodeURL(resultSet.getString("concept_id")))
                    .setPractitionerId(resultSet.getLong("practitionerId"));
            medicationStatementList.add(medicationStatement);
        }
        return medicationStatementList;
    }

    /**
     *
     * @param msId
     * @return
     * @throws Exception
     */
    public List<MedicationOrderFull> getMedicationOrderFullList(long msId, List<Long> patientIds) throws Exception {
        List<MedicationOrderFull> result = null;
        String sql = "SELECT mo.id, mo.practitioner_id as prid, mo.organization_id as oid, mo.medication_statement_id as msid, mo.clinical_effective_date as clinicalEffDt, mo.dose, mo.quantity_unit as qUnit, " +
                "mo.quantity_value as qValue FROM medication_order mo where mo.medication_statement_id=? " +
                " and mo.patient_id in (" + StringUtils.join(patientIds, ',') + ") " +
                "order by clinical_effective_date, msid";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, msId);
            try (ResultSet resultSet = statement.executeQuery()) {
                result = getMedicationOrderFullList(resultSet);
            }
        }
        return result;
    }

    /**
     *
     * @param resultSet
     * @return
     * @throws SQLException
     */
    public  List<MedicationOrderFull> getMedicationOrderFullList(ResultSet resultSet) throws SQLException {
        List<MedicationOrderFull> medicationOrderList = new ArrayList<MedicationOrderFull>();
        while (resultSet.next()) {
            MedicationOrderFull medicationOrder = new MedicationOrderFull();
            medicationOrder
                    .setId(resultSet.getLong("id"))
                    .setPractitionerId(resultSet.getLong("prid"))
                    .setOrgId(resultSet.getLong("oid"))
                    .setDate(resultSet.getString("clinicalEffDt"))
                    .setDose(resultSet.getString("dose"))
                    .setQValue(resultSet.getDouble("qValue"))
                    .setQUnit(resultSet.getString("qUnit"));
            medicationOrderList.add(medicationOrder);
        }
        return medicationOrderList;
    }

    public List<ConditionFull>  getConditionFullList(List<Long> patientIds , String dbType) throws Exception {
        ArrayList<ConditionFull> conditionFullList =null;
        String sql = "SELECT a.id as id,a.organization_id , a.patient_id as patientId, a.clinical_effective_date as date, a.encounter_id as encounterId, IF(ISNULL(a.problem_end_date), 'active', 'resolved') " +
                "AS ClinicalStatus, coalesce(c.description, c.name, '') as name ,c.code,c.id concept_id , v.name as category, a.is_problem as problem " +
                "FROM observation a left join concept c on c.dbid = a.non_core_concept_id " +
                "LEFT join value_set_member vsm on vsm.concept = a.non_core_concept_id "+
                "LEFT join  value_set v ON v.dbid = vsm.value_set ";
        String wherecondition="";
        if(dbType.equalsIgnoreCase(CONFIG_ACUTE))
        {
            wherecondition="  where  v.id in('http://endhealth.info/im#VSET_Conditions_1','http://endhealth.info/im#VSET_Microbiology','http://endhealth.info/im#VSET_SocialHistory','http://endhealth.info/im#VSET_VitalSigns') and patient_id in (" + StringUtils.join(patientIds, ',') + ")";

        }
        else {
            wherecondition="  where a.is_problem = 1 and a.is_review = 0 and a.clinical_effective_date is not null and v.id in('http://endhealth.info/im#VSET_Conditions_1') and a.patient_id in (" + StringUtils.join(patientIds, ',') + ") ";
        }
        sql=sql+wherecondition;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                conditionFullList= getConditionFullList(resultSet);
            }
        }
        return conditionFullList;
    }

   /* public List<ConditionFull>  getVisitDiagnosisFullList(List<Long> patientIds) throws Exception {
        ArrayList<ConditionFull> conditionFullList =null;
        String sql = "SELECT a.id as id, a.patient_id as patientId, a.clinical_effective_date as date,IF(ISNULL(a.problem_end_date), 'active', 'resolved') " +
                "AS ClinicalStatus, coalesce(c.description, c.name, '') as name ,c.code,c.id concept_id , v.name as category, a.is_problem as problem " +
                "FROM observation a left join concept c on c.dbid = a.non_core_concept_id " +
                "LEFT join value_set_member vsm on vsm.concept = a.non_core_concept_id "+
                "LEFT join  value_set v ON v.dbid = vsm.value_set " +
                " where a.is_problem = 0 and v.id in('http://endhealth.info/im#VSET_Conditions_1') and patient_id in (" + StringUtils.join(patientIds, ',') + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                conditionFullList= getConditionFullList(resultSet);
            }
        }
        return conditionFullList;
    } */

    public  ArrayList<ConditionFull> getConditionFullList(ResultSet resultSet) throws SQLException {
        ArrayList<ConditionFull> conditionlist=new ArrayList<ConditionFull>();
        if(null !=resultSet) {
            while (resultSet.next()) {
                ConditionFull conditionDtls = new ConditionFull();
                conditionDtls
                        .setId(resultSet.getLong("id"))
                        .setOrgId(resultSet.getLong("organization_id"))
                        .setPatientId(resultSet.getLong("patientId"))
                        .setDate(resultSet.getDate("date"))
                        .setName(resultSet.getString("name"))
                        .setCode(resultSet.getString("code"))
                        .setCategory(resultSet.getString("category"))
                        .setProblem(resultSet.getBoolean("problem"))
                        .setClinicalStatus(resultSet.getString("ClinicalStatus"))
                        .setCodeURL(getCodeURL(resultSet.getString("concept_id")))
                        .setEncounterId(resultSet.getLong("encounterId"));

                conditionlist.add(conditionDtls);
            }
        }
        return conditionlist;
    }

    public List<EncounterFull> getEncounterFullList(List<Long> patientIds, Long encounterId, boolean isPatient, String dbtype) throws Exception {
        ArrayList<EncounterFull> encounterFullList =null;

        String sql="";
         if(dbtype.equalsIgnoreCase(CONFIG_ACUTE)) {
             sql = " SELECT  e.patient_id as patientId, e.admission_method,e.service_provider_organization_id, e.practitioner_id as practitioner_id,e.organization_id as organization_id, " +
                     "DATE_FORMAT(e.clinical_effective_date,'%Y-%m-%d %H:%i:%s') as date, e.episode_of_care_id as episode_of_care_id, e.end_date as endDate, e.id, " +
                     " coalesce(c.name,'') as name ,coalesce(c.code,'') as code,  " +
                     " CASE WHEN e.end_date IS NULL THEN 'Active' ELSE 'Past' END as status " +
                     " FROM encounter e " +
                     " JOIN concept c on c.dbid = e.non_core_concept_id  ";

         }
         else {
              sql = " SELECT  e.patient_id as patientId, e.service_provider_organization_id, e.practitioner_id as practitioner_id,e.organization_id as organization_id, " +
                     "DATE_FORMAT(e.clinical_effective_date,'%Y-%m-%d %H:%i:%s') as date, e.episode_of_care_id as episode_of_care_id, e.end_date as endDate, e.id, " +
                     "coalesce(ct.encounter_type,'Administration') as name ,coalesce(c.code,'') as code,  " +
                     "CASE WHEN e.end_date IS NULL THEN 'Active' ELSE 'Past' END as status " +
                     " FROM encounter e " +
                     " JOIN concept c on c.dbid = e.non_core_concept_id " +
                     " join consultation_types ct on ct.original_code = c.id  ";
         }

        String where_clause="";
        if (isPatient) {
            where_clause = " where e.patient_id in (" + StringUtils.join(patientIds, ',') + ")";
        } else {
            where_clause = " where e.id = " + encounterId;
        }
        if(dbtype.equalsIgnoreCase(CONFIG_ACUTE)){
            sql=sql+where_clause + " and e.clinical_effective_date >= date_add(now(), interval -3 year) ";
        }
        else{
            sql=sql+where_clause + " and e.clinical_effective_date is not null and e.clinical_effective_date >= date_add(now(), interval -1 year) ";
        }

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                encounterFullList = getEncounterFullList(resultSet,dbtype);
            }
        }

        return encounterFullList;
    }

    public ArrayList<EncounterFull> getEncounterFullList(ResultSet resultSet,String dbtype) throws Exception {
        ArrayList<EncounterFull> encounterFullList=new ArrayList<EncounterFull>();
        if(null !=resultSet) {
            while (resultSet.next()) {
                EncounterFull encounterFull = new EncounterFull();
                encounterFull
                        .setPatientId(resultSet.getLong("patientId"))
                        .setPractitionerId(resultSet.getLong("practitioner_id"))
                        .setOrganizationId(resultSet.getLong("organization_id"))
                        .setDate(resultSet.getString("date"))
                        .setEndDate(resultSet.getString("endDate"))
                        .setName(resultSet.getString("name"))
                        .setCode(resultSet.getString("code"))
                        .setEncounterid(resultSet.getLong("id"))
                        .setStatus(resultSet.getString("status"))
                        .setSp_organizationId(resultSet.getLong("service_provider_organization_id"))
                        .setEpisode_of_care_id(resultSet.getString("episode_of_care_id"));
                if(dbtype.equalsIgnoreCase("ACUTE")) {
                   encounterFull.setSpecialityMap(getSpecialityDetails(encounterFull.getEncounterid()));
                   encounterFull.setRefConsultant(getRefConsultant(encounterFull.getEncounterid()));
                }

                encounterFullList.add(encounterFull);
            }
        }
        return encounterFullList;
    }




    //immunizations

    public List<ImmunizationFull> getImmunizationsFullList(List<Long> patientIds) throws Exception {
        ArrayList<ImmunizationFull> immunizationFullList =null;
        String sql = " SELECT o.id as id, o.organization_id, o.patient_id as patientId, o.clinical_effective_date as cfd, coalesce(o.encounter_id ,'') as encounterid ,coalesce(o.practitioner_id,'') as practitionerid, coalesce(c.description, c.name, '') as name ,c.code,c.id concept_id   " +
                     " FROM observation o  join concept c on c.dbid = o.non_core_concept_id " +
                    " join value_set_member vsm on vsm.concept = o.non_core_concept_id "+
                    " join  value_set v ON v.dbid = vsm.value_set "+
                    " where v.id = 'http://endhealth.info/im#VSET_Immunisations_CareConnect' and o.clinical_effective_date is not null and o.patient_id in (" + StringUtils.join(patientIds, ',') + ") " ;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                immunizationFullList = getImmunizationFullList(resultSet);
            }
        }

        return immunizationFullList;
    }

    public ArrayList<ImmunizationFull> getImmunizationFullList(ResultSet resultSet) throws SQLException {
        ArrayList<ImmunizationFull> immunizationFullList=new ArrayList<ImmunizationFull>();
        if(null !=resultSet) {
            while (resultSet.next()) {
                ImmunizationFull immunizationFull = new ImmunizationFull();
                immunizationFull
                        .setId(resultSet.getLong("id"))
                        .setOrgId(resultSet.getLong("organization_id"))
                        .setPatientId(resultSet.getLong("patientId"))
                        .setName(resultSet.getString("name"))
                        .setCode(resultSet.getString("code"))
                        .setClinicalEffectiveDate(resultSet.getDate("cfd"))
                        .setEncounterID(resultSet.getString("encounterid"))
                        .setPractitionerID(resultSet.getString("practitionerid"))
                        .setCodeURL(getCodeURL(resultSet.getString("concept_id")));




                immunizationFullList.add(immunizationFull);
            }
        }
        return immunizationFullList;
    }

    public List<AppointmentFull> getAppointmentFullList(List<Long> patientIds) throws Exception {
        List<AppointmentFull> result = null;
        String sql = "SELECT a.id, a.patient_id as patientId, a.schedule_id as sId, a.practitioner_id as prId, a.organization_id as oId, " +
                "a.actual_duration as actualDura, a.start_date as startDt, a.planned_duration as plannedDura, s.type,coalesce(c.description, c.name, '') as appointment_status  ," +
                "e.service_provider_organization_id ,  " +
                "e.service_provider_organization_id , aa.text_value" +
                " FROM appointment a left join schedule s on a.schedule_id = s.id" +
                " join concept c on c.dbid = a.appointment_status_concept_id " +
                " left join encounter e on e.appointment_id  =a.id " +
                "left join appointment_additional aa on aa.id=a.id " +
                "left join concept c1 on c1.dbid = aa.property_id " +
                " where a.patient_id in (" + StringUtils.join(patientIds, ',') + ") and aa.json_value is null and c1.code='DM_AppointmentType' ";

        sql=sql + " and a.start_date >= date_add(now(), interval -7 year) ";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                result = getAppointmentFullList(resultSet);
            }
        }
        return result;
    }
    /**
     *
     * @param resultSet
     * @return
     * @throws SQLException
     */
    public  List<AppointmentFull> getAppointmentFullList(ResultSet resultSet) throws Exception {
        List<AppointmentFull> appointmentList = new ArrayList<AppointmentFull>();
        while (resultSet.next()) {
            AppointmentFull appointment = new AppointmentFull();
            appointment
                    .setId(resultSet.getLong("id"))
                    .setPatientId(resultSet.getLong("patientId"))
                    .setActualDuration(resultSet.getInt("actualDura"))
                    .setStartDate(resultSet.getString("startDt"))
                    .setPlannedDuration(resultSet.getInt("plannedDura"))
                    .setType(resultSet.getString("text_value"))
                    .setOrgId(resultSet.getLong("oId"))
                    .setPractitionerId(resultSet.getLong("prId"))
                    .setScheduleId(resultSet.getLong("sId"))
                    .setStatus(resultSet.getString("appointment_status"))
                    .setSp_organizationId(resultSet.getLong("service_provider_organization_id"));
                appointment.setSpecialityMap( getAppointmentSpecialityDetails(appointment.getId()));
                appointment.setRefConsultant(getAppointmentRefConsultant(appointment.getId()));
                appointmentList.add(appointment);
        }
        return appointmentList;
    }

    /**
     *
     * @param patientIds
     * @return
     * @throws Exception
     */
    public List<FamilyMemberHistoryFull> getFamilyMemberHistoryFullList(List<Long> patientIds) throws Exception {
        List<FamilyMemberHistoryFull> result = null;
        String sql = "SELECT o.id as id, o.patient_id as patientId, o.clinical_effective_date as date, o.practitioner_id as practitionerId, o.organization_id as organizationId, " +
                "CASE WHEN o.problem_end_date IS NULL THEN 'Active' " +
                "ELSE 'Past' END as status,coalesce(c.description, c.name, '') as name,c.code,c.id concept_id " +
                "FROM observation o " +
                "join concept c on c.dbid = o.non_core_concept_id   "+
                " join value_set_member vsm on vsm.concept = o.non_core_concept_id " +
                " join  value_set v ON v.dbid = vsm.value_set "+
                " where v.id = 'http://endhealth.info/im#VSET_FamilyHistory' "+
                "and patient_id in (" + StringUtils.join(patientIds, ',') + ") and o.clinical_effective_date is not null" + " order by o.clinical_effective_date DESC";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    result = getFamilyMemberHistoryFullList(resultSet);
                }
            }

        return result;
    }

    /**
     *
     * @param resultSet
     * @return
     * @throws SQLException
     */
    public  List<FamilyMemberHistoryFull> getFamilyMemberHistoryFullList(ResultSet resultSet) throws SQLException {
        List<FamilyMemberHistoryFull> familyMemberHistoryList = new ArrayList<FamilyMemberHistoryFull>();
        while (resultSet.next()) {
            FamilyMemberHistoryFull familyMemberHistory = new FamilyMemberHistoryFull();
            familyMemberHistory
                    .setId(resultSet.getLong("id"))
                    .setPatientId(resultSet.getLong("patientId"))
                    .setDate(resultSet.getString("date"))
                    .setOrganizationId(resultSet.getString("organizationId"))
                    .setStatus(resultSet.getString("status"))
                    .setName(resultSet.getString("name"))
                    .setCode(resultSet.getString("code"))
                    .setPractitionerId(resultSet.getLong("practitionerId"))
                    .setCodeURL(getCodeURL(resultSet.getString("concept_id")));
            familyMemberHistoryList.add(familyMemberHistory);
        }
        return familyMemberHistoryList;
    }


    //ReferralRequest

    public List<ReferralRequestFull> getReferralRequestFullList(List<Long> patientIds) throws Exception {
        ArrayList<ReferralRequestFull> referralRequestFullList =null;
        String sql = "SELECT rr.id as id, rr.patient_id as patientId, rr.practitioner_id ,rr.organization_id as organization_id, rr.recipient_organization_id as recipent_orgid,rr.mode as intent,rr.clinical_effective_date as authored_on, "+
                " priorityConcept.name as priority, "+
                " refferalConcept.code as type_code,refferalConcept.id as concept_id, refferalConcept.name as type_display, "+
                " specialityConcept.name as speciality_name, specialityConcept.code as speciality_code, "+
                " ec.date_registered_end as EndDate "+
                " FROM referral_request rr "+
                " LEFT JOIN "+
                " concept priorityConcept "+
                " ON priorityConcept.dbid  = rr.referral_request_priority_concept_id "+
                " LEFT JOIN "+
                " concept refferalConcept "+
                " ON refferalConcept.dbid = rr.referral_request_type_concept_id "+
                " LEFT JOIN " +
                " concept specialityConcept " +
                " ON specialityConcept.dbid = rr.non_core_concept_id "+
                "LEFT JOIN episode_of_care ec ON ec.patient_id = rr.patient_id " +
                    " AND DATE(rr.clinical_effective_date) <= DATE(COALESCE(ec.date_registered_end, rr.clinical_effective_date)) " +
                    " AND DATE(rr.clinical_effective_date) >= DATE(ec.date_registered - INTERVAL 14 DAY) " +
                    " AND ec.date_registered = (SELECT MAX(eoc.date_registered) FROM episode_of_care eoc " +
                                                " WHERE eoc.patient_id = rr.patient_id "+
                                                        " AND DATE(rr.clinical_effective_date) <= DATE(COALESCE(eoc.date_registered_end, rr.clinical_effective_date)) "+
                                                        " AND DATE(rr.clinical_effective_date) >= DATE(eoc.date_registered - INTERVAL 14 DAY)) " +
                " where rr.patient_id in (" + StringUtils.join(patientIds, ',') + ") and rr.clinical_effective_date is not null ";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                referralRequestFullList = getReferralRequestFullList(resultSet);
            }
        }

        return referralRequestFullList;
    }

    private ArrayList<ReferralRequestFull> getReferralRequestFullList(ResultSet resultSet) throws SQLException {
        ArrayList<ReferralRequestFull> referralRequestFullList=new ArrayList<ReferralRequestFull>();
        if(null !=resultSet) {
            while (resultSet.next()) {
                ReferralRequestFull referralRequestFull = new ReferralRequestFull();
                referralRequestFull
                        .setId(resultSet.getString("id"))
                        .setPatientId(resultSet.getLong("patientId"))
                        .setPractitionerId(resultSet.getString("practitioner_id"))
                        .setRecipientOrganizationId(resultSet.getString("recipent_orgid"))
                        .setOrgId(resultSet.getString("organization_id"))
                        .setIntent(resultSet.getString("intent"))
                        .setClinicalEffectiveDate(resultSet.getDate("authored_on"))
                        .setPriority(resultSet.getString("priority"))
                        .setTypeCode(resultSet.getString("type_code"))
                        .setTypeDisplay(resultSet.getString("type_display"))
                        .setSpecialityName(resultSet.getString("speciality_name"))
                        .setSpecialityCode(resultSet.getString("speciality_code"))
                        .setCodeURL(getCodeURL(resultSet.getString("concept_id")))
                        .setEndDate(resultSet.getString("EndDate"));
                referralRequestFullList.add(referralRequestFull);
            }
        }
        return referralRequestFullList;
    }

    public Boolean applicationAccessProfile(String userId, String applicationAccessProfile) throws Exception {
        Boolean found = false;

        String sql = "select aap.name from user_manager.user_application_policy ua "+
        "join user_manager.application_policy_attribute apa on apa.application_policy_id = ua.application_policy_id "+
        "join user_manager.application_access_profile aap on aap.id = apa.application_access_profile_id "+
        "where ua.user_id = ? "+
        "and aap.name = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, applicationAccessProfile);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next())
                    found = true;
            }
        }

        return found;
    }

    public void saveHL7Message(String wrapper, String message, String payloadId) throws Exception {
        String sql = "INSERT INTO hl7v2_inbound.imperial (date_received, message_wrapper, hl7_message, payload_id) " +
                "VALUES (?, ?, ?, ?)";

        java.sql.Timestamp now = new java.sql.Timestamp(System.currentTimeMillis());

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setTimestamp(1, now);
            stmt.setString(2, wrapper);
            stmt.setString(3, message);
            stmt.setString(4, payloadId);
            stmt.executeUpdate();
        }
    }


    @Override
    public void close() throws Exception {
        LOG.info("closing the JDBCDal");
        if (connection != null) {
            this.connection.close();
        }
    }

    public List<AlertFull> getAlertFullList(List<Long> patientIds) throws Exception {
        List<AlertFull> result = null;
        String sql =  "SELECT id,   organization_id,   effective_date,     is_active,    flag_text, patient_id  "+
                        "  FROM flag  where is_active=1 and patient_id in (" + StringUtils.join(patientIds, ',') + ") ";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                result = getAlertFullList(resultSet);
            }
        }
        return result;
    }
    /**
     *
     * @param resultSet
     * @return
     * @throws SQLException
     */
    public List<AlertFull> getAlertFullList(ResultSet resultSet) throws SQLException {
        List<AlertFull> alertList = new ArrayList<AlertFull>();
        while (resultSet.next()) {
            AlertFull alert = new AlertFull();
            alert
                    .setId(resultSet.getLong("id"))
                    .setEffective_date(resultSet.getString("effective_date"))
                    .setFlag_text(resultSet.getString("flag_text"))
                    .setIs_active(resultSet.getInt("is_active"))
                    .setOrganization_id(resultSet.getString("organization_id"))
                    .setPatient_id(resultSet.getLong("patient_id"));
            alertList.add(alert);
        }
        return alertList;
    }

    public Map<String,String> getSpecialityDetails(long encounter_Id) throws Exception {
        return prepareSpecialityMap( connection,getEncounterSpecialityQuery(encounter_Id));
    }

    public Map<String,String> getAppointmentSpecialityDetails(long appointment_Id) throws Exception {
         return prepareSpecialityMap( connection,getAppointmentSpecialityQuery(appointment_Id));
    }

    private Map<String, String> prepareSpecialityMap(Connection connection, String sql) throws SQLException {
        Map<String,String> specialityMap = new HashMap<String,String>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    specialityMap.put("code",resultSet.getString("code"));
                    specialityMap.put("value",resultSet.getString("name"));
                }
            }
        }
        return specialityMap;
    }

    private String getRefConsultant(long encounterid) throws SQLException {
        String sql = " select ea.text_value from encounter_additional ea" +
                " join concept concept on concept.dbid=ea.property_id" +
                " where ea.json_value is null and concept.code='DM_ReferringConsultant' and " +
                " ea.id in ( select id from encounter_event where encounter_id="+encounterid+") limit 1 ";
        return prepareRefcon( connection,sql);
    }

    private String getAppointmentRefConsultant(long appointmentId) throws SQLException {

        String sql = " select ea.text_value  from encounter_additional ea" +
                " join concept concept on concept.dbid=ea.property_id" +
                " where ea.json_value is null and concept.code='DM_ReferringConsultant'  " +
                "and ea.id in ( select id from encounter_event where encounter_id in " +
                "(select id from encounter e where e.appointment_id= "+appointmentId+"))  limit 1 ";
        return prepareRefcon( connection,sql);
    }

    private String prepareRefcon(Connection connection, String sql) throws SQLException {
        String refcon=null;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    refcon=resultSet.getString("text_value");
                }


            }
        }
        return  refcon;
    }
  public String recipientOrg(long id) throws SQLException {
      String recipientOrg=null;
      String sql = " SELECT rra.text_value " +
              " FROM referral_request_additional rra " +
              " JOIN concept AS ca ON ca.dbid = rra.property_id" + " and ca.code='DM_ReferralRecipient' " +
              "WHERE rra.id in ("+ id +")";


      try (PreparedStatement statement = connection.prepareStatement(sql)) {
          try (ResultSet resultSet = statement.executeQuery()) {
              while (resultSet.next()) {
                  recipientOrg=resultSet.getString("text_value");
              }


          }
      }
      return  recipientOrg;
  }

    public String serviceOffered(long id) throws SQLException {
        String serviceOffered=null;
        String sql = " SELECT rra.text_value " +
                " FROM referral_request_additional rra " +
                " JOIN concept AS ca ON ca.dbid = rra.property_id" + " and ca.code='DM_ReferralServiceOffered' " +
                "WHERE rra.id in ("+ id +") and rra.text_value not in ('Static Site')";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    serviceOffered=resultSet.getString("text_value");
                }


            }
        }
        return  serviceOffered;
    }

    public long getOrgId(String odsCode) throws SQLException {
        long orgId = 0;
              String query = "SELECT o.id " +
                "FROM organization o " +
                "WHERE o.ods_code IN (?)";

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, odsCode);
            try (ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    orgId = resultSet.getLong("id");
                }
            }

            return orgId;
        }
    }
}
