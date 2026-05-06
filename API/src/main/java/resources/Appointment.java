package resources;

import org.endeavourhealth.getFHIRRecordAPI.common.constants.ResourceConstants;
import org.endeavourhealth.getFHIRRecordAPI.common.models.AppointmentFull;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Appointment {

	/**
	 *
	 * @param appointmentResult
	 * @return
	 * @throws Exception
	 */
	public static org.hl7.fhir.dstu3.model.Appointment getAppointmentResource(AppointmentFull appointmentResult) throws Exception {
		org.hl7.fhir.dstu3.model.Appointment appointment = new org.hl7.fhir.dstu3.model.Appointment();
		appointment.setId(String.valueOf(UUID.randomUUID()));
		appointment.getMeta().addProfile("https://fhir.hl7.org.uk/STU3/StructureDefinition/CareConnect-Appointment-1");
		appointment.addIdentifier()
				.setValue(String.valueOf(appointmentResult.getId()))
				.setSystem(ResourceConstants.SYSTEM_ID);

		Date startDt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(replaceNull(appointmentResult.getStartDate()));
		appointment.setStart(startDt);
		//appointment.setEnd(new Date(startDt.getTime()+ TimeUnit.MINUTES.toMinutes(appointmentResult.getActualDuration())));
		appointment.setEnd(addMinutesToDate(appointmentResult.getActualDuration(),startDt));
		if(appointmentResult.getStatus()!=null)
			appointment.setStatus(new org.hl7.fhir.dstu3.model.Appointment
					.AppointmentStatusEnumFactory()
					.fromCode(appointmentResult.getStatus()
							.toLowerCase().replaceAll(" ", "")));

		if(null!=appointmentResult.getType()) {
			Coding coding = new Coding();
			coding.setSystem("http://hl7.org/fhir/STU3/v2/0276/index.html");
			coding.setDisplay(replaceNull(appointmentResult.getType()));
			appointment.getAppointmentType().addCoding(coding).setText(appointmentResult.getType());
		}

		if(null !=appointmentResult.getSpecialityMap().get("code"))
		{
			//  Encounter -> type -> coding -> code (code value)
//                            Display ( description)
			// -> text (description)
			CodeableConcept code = new CodeableConcept();
			code.addCoding()
					.setCode(appointmentResult.getSpecialityMap().get("code"))
					.setDisplay(appointmentResult.getSpecialityMap().get("value"));
			//.setSystem("http://hl7.org/fhir/ValueSet/encounter-participant-type");
			code.setText(appointmentResult.getSpecialityMap().get("value"));

			List codes=  new ArrayList<CodeableConcept>();
			codes.add(code);
			appointment.setSpecialty(codes);


		}
		return appointment;
	}
	static final long ONE_MINUTE_IN_MILLIS = 60000;
	public static Date addMinutesToDate(int minutes, Date beforeTime) {

		long curTimeInMs = beforeTime.getTime();
		Date afterAddingMins = new Date(curTimeInMs
				+ (minutes * ONE_MINUTE_IN_MILLIS));
		return afterAddingMins;
	}

	/**
	 *
	 * @param input
	 * @return
	 */
	public static String replaceNull(String input) {
		return input == null ? "" : input;
	}


}
