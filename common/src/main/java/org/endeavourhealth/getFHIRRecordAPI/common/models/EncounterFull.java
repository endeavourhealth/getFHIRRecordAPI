package org.endeavourhealth.getFHIRRecordAPI.common.models;

import java.util.Map;

public class EncounterFull {

    private Map<String,String> specialityMap;
    public String getStatus() {
        return status;
    }

    public long getEncounterid() {
        return encounterid;
    }

    public Map<String, String> getSpecialityMap() {
        return specialityMap;
    }

    public void setSpecialityMap(Map<String, String> specialityMap) {
        this.specialityMap = specialityMap;
    }

    public EncounterFull setEncounterid(long encounterid) {
        this.encounterid = encounterid;
        return this;
    }

    public EncounterFull setStatus(String status) {
        this.status = status;
        return this;
    }

    public String getName() {
        return name;
    }

    public EncounterFull setName(String name) {
        this.name = name;
        return this;
    }

    public String getCode() {
        return code;
    }

    public EncounterFull setCode(String code) {
        this.code = code;
        return this;
    }

    public long getPatientId() {
        return patientId;
    }

    public EncounterFull setPatientId(long patientId) {
        this.patientId = patientId;
        return this;
    }

    public String getDate() {
        return date;
    }

    public EncounterFull setDate(String date) {
        this.date = date;
        return this;
    }

    public String getEndDate() {
        return endDate;
    }

    public EncounterFull setEndDate(String endDate) {
        this.endDate = endDate;
        return this;
    }

    public String getEpisode_of_care_id() {
        return episode_of_care_id;
    }

    public long getPractitionerId() {
        return practitionerId;
    }

    public EncounterFull setPractitionerId(long practitionerId) {
        this.practitionerId = practitionerId;
        return this;
    }

    public EncounterFull setEpisode_of_care_id(String episode_of_care_id) {
        this.episode_of_care_id = episode_of_care_id;
        return this;
    }

    public long getOrganizationId() {
        return organizationId;
    }

    public EncounterFull setOrganizationId(long organizationId) {
        this.organizationId = organizationId;
        return this;
    }




    private String status;
    private String name;

    public long getSp_organizationId() {
        return sp_organizationId;
    }

    public EncounterFull setSp_organizationId(long sp_organizationId) {
        this.sp_organizationId = sp_organizationId;
        return this;
    }

    private String code;
    private long encounterid;
    private long patientId;

    private String date;
    private String endDate;
    private String episode_of_care_id;
    private long practitionerId=-1;
    private long organizationId;

    public String getRefConsultant() {
        return refConsultant;
    }

    public void setRefConsultant(String refConsultant) {
        this.refConsultant = refConsultant;
    }

    private long sp_organizationId;
  private String refConsultant;


}
