package org.endeavourhealth.getFHIRRecordAPI.common.models;

import java.util.Date;

public class AlertFull {
    long id;
    String organization_id;
    String effective_date;
    int is_active;

    public long getPatient_id() {
        return patient_id;
    }

    public AlertFull setPatient_id(long patient_id) {
        this.patient_id = patient_id;
        return this;
    }

    long patient_id;

    public long getId() {
        return id;
    }

    public AlertFull setId(long id) {
        this.id = id;
        return this;
    }

    public String getOrganization_id() {
        return organization_id;
    }

    public AlertFull setOrganization_id(String organization_id) {
        this.organization_id = organization_id;
        return this;
    }

    public String getEffective_date() {
        return effective_date;
    }

    public AlertFull setEffective_date(String effective_date) {
        this.effective_date = effective_date;
        return this;
    }

    public int getIs_active() {
        return is_active;
    }

    public AlertFull setIs_active(int is_active) {
        this.is_active = is_active;
        return this;
    }

    public String getFlag_text() {
        return flag_text;
    }

    public AlertFull setFlag_text(String flag_text) {
        this.flag_text = flag_text;
        return this;
    }

    String flag_text;

}
