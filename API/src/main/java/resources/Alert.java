package resources;

import org.endeavourhealth.getFHIRRecordAPI.common.constants.ResourceConstants;
import org.endeavourhealth.getFHIRRecordAPI.common.models.AlertFull;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Flag;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class Alert {
    public static org.hl7.fhir.dstu3.model.Flag getAlertResource(AlertFull alertfull) throws Exception {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date date = formatter.parse(alertfull.getEffective_date());

        org.hl7.fhir.dstu3.model.Flag alert = new org.hl7.fhir.dstu3.model.Flag();
        alert.getMeta().addProfile("https://fhir.hl7.org.uk/STU3/StructureDefinition/CareConnect-Flag-1");
        alert.addIdentifier()
                .setValue(String.valueOf(alertfull.getId()))
                .setSystem(ResourceConstants.SYSTEM_ID);
        alert.setId(UUID.randomUUID().toString());
        CodeableConcept code = new CodeableConcept();
        code.setText(alertfull.getFlag_text());
        alert.setCode(code);

         org.hl7.fhir.dstu3.model.Period period = new org.hl7.fhir.dstu3.model.Period();
         period.setStart(date);
         alert.setStatus(Flag.FlagStatus.fromCode("active"));
         alert.setPeriod(period);

        return alert;
    }
}