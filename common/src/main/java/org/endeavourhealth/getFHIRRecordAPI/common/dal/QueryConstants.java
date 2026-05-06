package org.endeavourhealth.getFHIRRecordAPI.common.dal;

public class QueryConstants {

    public static String getAppointmentSpecialityQuery(long appointment_Id) {
        return new StringBuilder("SELECT con.code  ,con.name FROM  appointment_additional aa ")
                .append("join concept concept on concept.dbid=aa.property_id  and  concept.code='DM_treatmentFunctionAdmit' ")
                .append("join concept con on aa.value_id = con.dbid ")
                .append("where aa.id= ")
                .append(appointment_Id).toString();
    }

    public static String getEncounterSpecialityQuery(long encounter_Id) {
        StringBuilder query= new StringBuilder("Select concept2.code ,concept2.name from encounter_additional ea ")
                .append(" join concept concept on concept.dbid=ea.property_id join concept concept2 on concept2.dbid=ea.value_id ")
                .append(" where ea.json_value is null and concept.code='DM_treatmentFunctionAdmit' and")
                .append(" ea.id in ( select id from encounter_event where encounter_id="+encounter_Id+")")
                .append("  union")
                .append(" Select concept2.code ,concept2.name from encounter_additional ea ")
                .append(" join concept concept on concept.dbid=ea.property_id ")
                .append(" join concept concept2 on concept2.dbid=ea.value_id ")
                .append(" where ea.json_value is null and concept.code='DM_treatmentFunctionAdmit' and ea.id="+encounter_Id+" limit 1");
        return query.toString();
  }

    public static void main(String[] args) {
        System.out.println(getEncounterSpecialityQuery(41));

    }


}
