package org.endeavourhealth.getFHIRRecordAPI.api.process;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.dstu3.model.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;

public class PalantirBundleMapper {
    private static final Logger LOG = LoggerFactory.getLogger(PalantirBundleMapper.class);
    private static final String SYSTEM_NHS_NUMBER = "https://fhir.hl7.org.uk/Id/nhs-number";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    public static Map<String, Object> mapToFhirToPalantir(JSONObject bundleJson, List<String> selectProperties, boolean excludeRid) {
        LOG.info("Mapping FHIR Bundle to Palantir OntologyObjectV2 format");

        Map<String, Object> result = new LinkedHashMap<>();

        try {
            String bundleString = bundleJson.toJSONString();
            org.hl7.fhir.dstu3.model.Bundle fhirBundle = parseBundle(bundleString);

            String nhsNumber = extractNhsNumber(fhirBundle);
            String patientId = generateRid(nhsNumber);

            if (!excludeRid) {
                result.put("__rid", patientId);
                result.put("__primaryKey", nhsNumber);
                result.put("__apiName", "Patient");
            }

            List<org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent> entries = fhirBundle.getEntry();

            org.hl7.fhir.dstu3.model.Patient patientResource = null;
            List<Map<String, Object>> observations = new ArrayList<>();
            List<Map<String, Object>> conditions = new ArrayList<>();
            List<Map<String, Object>> allergies = new ArrayList<>();
            List<Map<String, Object>> medications = new ArrayList<>();
            List<Map<String, Object>> encounters = new ArrayList<>();
            List<Map<String, Object>> immunizations = new ArrayList<>();
            List<Map<String, Object>> appointments = new ArrayList<>();
            List<Map<String, Object>> procedures = new ArrayList<>();
            List<Map<String, Object>> familyHistory = new ArrayList<>();
            List<Map<String, Object>> referrals = new ArrayList<>();
            List<Map<String, Object>> alerts = new ArrayList<>();

            Map<String, Map<String, String>> organizations = new LinkedHashMap<>();
            Map<String, Map<String, String>> practitioners = new LinkedHashMap<>();

            for (org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent entry : entries) {
                Resource resource = entry.getResource();

                if (resource instanceof org.hl7.fhir.dstu3.model.Patient) {
                    patientResource = (org.hl7.fhir.dstu3.model.Patient) resource;
                } else if (resource instanceof Observation) {
                    observations.add(mapObservation((Observation) resource));
                } else if (resource instanceof Condition) {
                    conditions.add(mapCondition((Condition) resource));
                } else if (resource instanceof AllergyIntolerance) {
                    allergies.add(mapAllergy((AllergyIntolerance) resource));
                } else if (resource instanceof MedicationStatement) {
                    medications.add(mapMedicationStatement((MedicationStatement) resource));
                } else if (resource instanceof Encounter) {
                    encounters.add(mapEncounter((Encounter) resource));
                } else if (resource instanceof Immunization) {
                    immunizations.add(mapImmunization((Immunization) resource));
                } else if (resource instanceof Appointment) {
                    appointments.add(mapAppointment((Appointment) resource));
                } else if (resource instanceof Procedure) {
                    procedures.add(mapProcedure((Procedure) resource));
                } else if (resource instanceof FamilyMemberHistory) {
                    familyHistory.add(mapFamilyMemberHistory((FamilyMemberHistory) resource));
                } else if (resource instanceof ReferralRequest) {
                    referrals.add(mapReferralRequest((ReferralRequest) resource));
                } else if (resource instanceof Flag) {
                    alerts.add(mapAlert((Flag) resource));
                } else if (resource instanceof Organization) {
                    organizations.putAll(mapOrganization((Organization) resource));
                } else if (resource instanceof Practitioner) {
                    practitioners.putAll(mapPractitioner((Practitioner) resource));
                }
            }

            if (patientResource != null) {
                result.putAll(mapPatient(patientResource, nhsNumber));
            }

            result.put("observations", observations);
            result.put("conditions", conditions);
            result.put("allergies", allergies);
            result.put("medications", medications);
            result.put("encounters", encounters);
            result.put("immunizations", immunizations);
            result.put("appointments", appointments);
            result.put("procedures", procedures);
            result.put("familyHistory", familyHistory);
            result.put("referrals", referrals);
            result.put("alerts", alerts);
            result.put("organizations", organizations);
            result.put("practitioners", practitioners);

        } catch (Exception e) {
            LOG.error("Error mapping FHIR Bundle to Palantir format", e);
            throw new RuntimeException("Failed to map FHIR Bundle: " + e.getMessage(), e);
        }

        if (selectProperties != null && !selectProperties.isEmpty()) {
            result = filterBySelect(result, selectProperties);
        }

        return result;
    }

    private static org.hl7.fhir.dstu3.model.Bundle parseBundle(String bundleString) throws ParseException {
        JSONParser parser = new JSONParser();
        JSONObject bundleObj = (JSONObject) parser.parse(bundleString);
        FhirContext ctx = FhirContext.forDstu3();
        return (org.hl7.fhir.dstu3.model.Bundle) ctx.newJsonParser().parseResource(bundleObj.toJSONString());
    }

    private static String extractNhsNumber(org.hl7.fhir.dstu3.model.Bundle bundle) {
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            if (entry.getResource() instanceof Patient) {
                Patient patient = (Patient) entry.getResource();
                for (Identifier identifier : patient.getIdentifier()) {
                    if (SYSTEM_NHS_NUMBER.equals(identifier.getSystem())) {
                        return identifier.getValue();
                    }
                }
            }
        }
        return "unknown";
    }

    private static String generateRid(String nhsNumber) {
        return "ri.phonograph2-objects.main.object." + nhsNumber.replace("-", "");
    }

    private static Map<String, Object> mapPatient(Patient patient, String nhsNumber) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (Identifier identifier : patient.getIdentifier()) {
            if (SYSTEM_NHS_NUMBER.equals(identifier.getSystem())) {
                result.put("nhsNumber", identifier.getValue());
            }
        }

        if (!patient.getName().isEmpty()) {
            HumanName name = patient.getName().get(0);
            if (name.hasPrefix()) {
                List<String> prefixes = new ArrayList<>();
                for (StringType s : name.getPrefix()) { prefixes.add(s.getValue()); }
                result.put("title", String.join(", ", prefixes));
            }
            if (name.hasFamily()) {
                result.put("lastName", name.getFamily());
            }
            if (name.hasGiven()) {
                List<String> givenNames = new ArrayList<>();
                for (StringType s : name.getGiven()) { givenNames.add(s.getValue()); }
                result.put("firstName", String.join(" ", givenNames));
            }
        }

        if (patient.hasGender()) {
            result.put("gender", patient.getGender().toCode());
        }

        if (patient.hasBirthDate()) {
            result.put("dateOfBirth", DATE_FORMAT.format(patient.getBirthDate()));
        }

        if (patient.hasDeceased()) {
            Type deceased = patient.getDeceased();
            if (deceased instanceof DateTimeType) {
                result.put("dateOfDeath", DATE_FORMAT.format(((DateTimeType) deceased).getValue()));
            } else if (deceased instanceof BooleanType) {
                result.put("dateOfDeath", null);
            }
        }

        if (!patient.getAddress().isEmpty()) {
            Address address = patient.getAddress().get(0);
            if (address.hasLine()) {
                List<StringType> lines = address.getLine();
                for (int i = 0; i < Math.min(4, lines.size()); i++) {
                    result.put("addressLine" + (i + 1), lines.get(i).getValue());
                }
            }
            if (address.hasCity()) {
                result.put("city", address.getCity());
            }
            if (address.hasPostalCode()) {
                result.put("postcode", address.getPostalCode());
            }
        }

        for (ContactPoint telecom : patient.getTelecom()) {
            String system = telecom.getSystem().toCode();
            if ("phone".equalsIgnoreCase(system)) {
                result.put("phone", telecom.getValue());
            } else if ("mobile".equalsIgnoreCase(system)) {
                result.put("mobile", telecom.getValue());
            } else if ("email".equalsIgnoreCase(system)) {
                result.put("email", telecom.getValue());
            }
        }

        for (Extension extension : patient.getExtension()) {
            String url = extension.getUrl();
            if (url.contains("ReligiousAffiliation")) {
                Map<String, Object> religion = new LinkedHashMap<>();
                if (extension.hasValue() && extension.getValue() instanceof CodeableConcept) {
                    CodeableConcept cc = (CodeableConcept) extension.getValue();
                    if (!cc.getCoding().isEmpty()) {
                        Coding coding = cc.getCoding().get(0);
                        religion.put("code", coding.getCode());
                        religion.put("display", coding.getDisplay());
                    }
                }
                result.put("religion", religion.isEmpty() ? null : religion);
            } else if (url.contains("patient-religion") || url.contains("ReligiousAffiliation")) {
                Map<String, Object> religion = new LinkedHashMap<>();
                if (extension.hasValue() && extension.getValue() instanceof CodeableConcept) {
                    CodeableConcept cc = (CodeableConcept) extension.getValue();
                    for (Coding coding : cc.getCoding()) {
                        religion.put("code", coding.getCode());
                        religion.put("display", coding.getDisplay());
                    }
                }
                result.put("religion", religion.isEmpty() ? null : religion);
            } else if (url.contains("RegistrationDetails")) {
                for (Extension ext : extension.getExtension()) {
                    if ("registrationPeriod".equals(ext.getUrl())) {
                        if (ext.hasValue() && ext.getValue() instanceof Period) {
                            Period period = (Period) ext.getValue();
                            Map<String, Object> registration = new LinkedHashMap<>();
                            if (period.hasStart()) {
                                registration.put("startDate", DATE_FORMAT.format(period.getStart()));
                            }
                            if (period.hasEnd()) {
                                registration.put("endDate", DATE_FORMAT.format(period.getEnd()));
                            }
                            result.put("registrationPeriod", registration);
                        }
                    }
                }
            }
        }

        for (Patient.PatientCommunicationComponent comm : patient.getCommunication()) {
            if (comm.hasLanguage()) {
                CodeableConcept lang = comm.getLanguage();
                Map<String, Object> language = new LinkedHashMap<>();
                if (!lang.getCoding().isEmpty()) {
                    Coding coding = lang.getCoding().get(0);
                    language.put("code", coding.getCode());
                    language.put("display", coding.getDisplay());
                }
                result.put("preferredLanguage", language.isEmpty() ? null : language);
            }
        }

        for (Extension extension : patient.getExtension()) {
            if (extension.getUrl().contains("EthnicCategory")) {
                if (extension.hasValue() && extension.getValue() instanceof CodeableConcept) {
                    CodeableConcept cc = (CodeableConcept) extension.getValue();
                    if (!cc.getCoding().isEmpty()) {
                        result.put("ethnicity", cc.getCoding().get(0).getDisplay());
                    }
                }
            }
        }

        return result;
    }

    private static Map<String, Object> mapObservation(Observation observation) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!observation.getCode().getCoding().isEmpty()) {
            Coding coding = observation.getCode().getCoding().get(0);
            result.put("code", coding.getCode());
            result.put("display", coding.getDisplay());
        }

        if (observation.hasStatus()) {
            result.put("status", observation.getStatus().toCode());
        }

        if (observation.hasCategory()) {
            for (Coding category : observation.getCategory().get(0).getCoding()) {
                result.put("category", category.getCode());
            }
        }

        if (observation.hasValue()) {
            Type value = observation.getValue();
            if (value instanceof Quantity) {
                Quantity qty = (Quantity) value;
                result.put("value", qty.getValue() != null ? qty.getValue().doubleValue() : null);
                if (qty.hasUnit()) {
                    result.put("unit", qty.getUnit());
                } else if (qty.hasSystem() && qty.hasCode()) {
                    result.put("unit", qty.getCode());
                }
            } else if (value instanceof StringType) {
                result.put("value", ((StringType) value).getValue());
            } else if (value instanceof IntegerType) {
                result.put("value", ((IntegerType) value).getValue());
            } else if (value instanceof BooleanType) {
                result.put("value", ((BooleanType) value).getValue());
            }
        }

        if (observation.hasEffective()) {
            Type effective = observation.getEffective();
            if (effective instanceof DateTimeType) {
                result.put("date", DATE_FORMAT.format(((DateTimeType) effective).getValue()));
            } else if (effective instanceof Period) {
                Period period = (Period) effective;
                if (period.hasStart()) {
                    result.put("date", DATE_FORMAT.format(period.getStart()));
                }
            } else if (effective instanceof DateType) {
                result.put("date", DATE_FORMAT.format(((DateType) effective).getValue()));
            }
        }

        return result;
    }

    private static Map<String, Object> mapCondition(Condition condition) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!condition.getCode().getCoding().isEmpty()) {
            Coding coding = condition.getCode().getCoding().get(0);
            result.put("code", coding.getCode());
            result.put("display", coding.getDisplay());
        }

        if (condition.hasClinicalStatus()) {
            result.put("clinicalStatus", condition.getClinicalStatus().toCode());
        }

        if (condition.hasVerificationStatus()) {
            result.put("verificationStatus", condition.getVerificationStatus().toCode());
        }

        if (condition.hasCategory()) {
            for (Coding category : condition.getCategory().get(0).getCoding()) {
                result.put("category", category.getCode());
            }
        }

        if (condition.hasOnset()) {
            Type onset = condition.getOnset();
            if (onset instanceof DateTimeType) {
                result.put("onsetDate", DATE_FORMAT.format(((DateTimeType) onset).getValue()));
            } else if (onset instanceof Period) {
                Period period = (Period) onset;
                if (period.hasStart()) {
                    result.put("onsetDate", DATE_FORMAT.format(period.getStart()));
                }
            } else if (onset instanceof DateType) {
                result.put("onsetDate", DATE_FORMAT.format(((DateType) onset).getValue()));
            }
        }

        if (condition.hasAbatement()) {
            Type abatement = condition.getAbatement();
            if (abatement instanceof DateTimeType) {
                result.put("abatementDate", DATE_FORMAT.format(((DateTimeType) abatement).getValue()));
            } else if (abatement instanceof DateType) {
                result.put("abatementDate", DATE_FORMAT.format(((DateType) abatement).getValue()));
            }
        }

        return result;
    }

    private static Map<String, Object> mapAllergy(AllergyIntolerance allergy) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!allergy.getCode().getCoding().isEmpty()) {
            Coding coding = allergy.getCode().getCoding().get(0);
            result.put("code", coding.getCode());
            result.put("display", coding.getDisplay());
        }

        if (allergy.hasType()) {
            result.put("type", allergy.getType().toCode());
        }

        if (allergy.hasClinicalStatus()) {
            result.put("clinicalStatus", allergy.getClinicalStatus().toCode());
        }

        if (allergy.hasReaction() && !allergy.getReaction().isEmpty()) {
            List<Map<String, String>> reactions = new ArrayList<>();
            for (AllergyIntolerance.AllergyIntoleranceReactionComponent reaction : allergy.getReaction()) {
                Map<String, String> reactionMap = new LinkedHashMap<>();
                if (reaction.hasSubstance() && !reaction.getSubstance().getCoding().isEmpty()) {
                    reactionMap.put("substance", reaction.getSubstance().getCoding().get(0).getDisplay());
                }
                if (reaction.hasManifestation() && !reaction.getManifestation().isEmpty()) {
                    CodeableConcept manifest = reaction.getManifestation().get(0);
                    if (!manifest.getCoding().isEmpty()) {
                        reactionMap.put("manifestation", manifest.getCoding().get(0).getDisplay());
                    }
                }
                reactions.add(reactionMap);
            }
            result.put("reaction", reactions);
        }

        return result;
    }

    private static Map<String, Object> mapMedicationStatement(MedicationStatement med) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (med.hasMedication()) {
            if (med.getMedication() instanceof Reference && ((Reference) med.getMedication()).hasDisplay()) {
                result.put("medicationCode", ((Reference) med.getMedication()).getDisplay());
            } else if (med.getMedication() instanceof CodeableConcept) {
                CodeableConcept cc = (CodeableConcept) med.getMedication();
                if (!cc.getCoding().isEmpty()) {
                    Coding coding = cc.getCoding().get(0);
                    result.put("medicationCode", coding.getCode());
                    result.put("medicationDisplay", coding.getDisplay());
                }
            }
        }

        if (med.hasStatus()) {
            result.put("status", med.getStatus().toCode());
        }

        if (med.hasEffective()) {
            Type effective = med.getEffective();
            if (effective instanceof DateTimeType) {
                result.put("effectiveDate", DATE_FORMAT.format(((DateTimeType) effective).getValue()));
            } else if (effective instanceof Period) {
                Period period = (Period) effective;
                if (period.hasStart()) {
                    result.put("effectiveDate", DATE_FORMAT.format(period.getStart()));
                }
            }
        }

        if (med.hasDosage() && !med.getDosage().isEmpty()) {
            org.hl7.fhir.dstu3.model.Dosage dosage = med.getDosage().get(0);
            if (dosage.hasText()) {
                result.put("dosageText", dosage.getText());
            }
        }

        return result;
    }

    private static Map<String, Object> mapEncounter(Encounter encounter) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!encounter.getType().isEmpty()) {
            CodeableConcept type = encounter.getType().get(0);
            if (!type.getCoding().isEmpty()) {
                Coding coding = type.getCoding().get(0);
                result.put("type", coding.getCode());
                result.put("display", coding.getDisplay());
            }
        }

        if (encounter.hasStatus()) {
            result.put("status", encounter.getStatus().toCode());
        }

        if (encounter.hasClass_()) {
            result.put("class", encounter.getClass_().getCode());
        }

        if (encounter.hasPeriod()) {
            Period period = encounter.getPeriod();
            if (period.hasStart()) {
                result.put("startDate", DATE_FORMAT.format(period.getStart()));
            }
            if (period.hasEnd()) {
                result.put("endDate", DATE_FORMAT.format(period.getEnd()));
            }
        }

        if (!encounter.getLocation().isEmpty()) {
            Encounter.EncounterLocationComponent loc = encounter.getLocation().get(0);
            if (loc.hasLocation() && loc.getLocation().hasDisplay()) {
                result.put("location", loc.getLocation().getDisplay());
            }
        }

        return result;
    }

    private static Map<String, Object> mapImmunization(Immunization immunization) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!immunization.getVaccineCode().getCoding().isEmpty()) {
            Coding coding = immunization.getVaccineCode().getCoding().get(0);
            result.put("vaccineCode", coding.getCode());
            result.put("display", coding.getDisplay());
        }

        if (immunization.hasStatus()) {
            result.put("status", immunization.getStatus().toCode());
        }

        if (immunization.hasDate()) {
            result.put("occurrenceDate", DATE_FORMAT.format(immunization.getDate()));
        }

        if (!immunization.getPractitioner().isEmpty()) {
            Immunization.ImmunizationPractitionerComponent pract = immunization.getPractitioner().get(0);
            if (pract.hasActor() && pract.getActor().hasDisplay()) {
                result.put("performer", pract.getActor().getDisplay());
            }
        }

        if (immunization.hasLotNumber()) {
            result.put("lotNumber", immunization.getLotNumber());
        }

        return result;
    }

    private static Map<String, Object> mapAppointment(Appointment appointment) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (appointment.hasServiceCategory() && appointment.getServiceCategory() != null) {
            CodeableConcept category = appointment.getServiceCategory();
            if (!category.getCoding().isEmpty()) {
                Coding coding = category.getCoding().get(0);
                result.put("serviceType", coding.getCode());
                result.put("display", coding.getDisplay());
            }
        }

        if (appointment.hasStatus()) {
            result.put("status", appointment.getStatus().toCode());
        }

        if (appointment.hasStart()) {
            result.put("start", DATE_FORMAT.format(appointment.getStart()));
        }

        if (appointment.hasEnd()) {
            result.put("end", DATE_FORMAT.format(appointment.getEnd()));
        }

        return result;
    }

    private static Map<String, Object> mapProcedure(Procedure procedure) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!procedure.getCode().getCoding().isEmpty()) {
            Coding coding = procedure.getCode().getCoding().get(0);
            result.put("code", coding.getCode());
            result.put("display", coding.getDisplay());
        }

        if (procedure.hasStatus()) {
            result.put("status", procedure.getStatus().toCode());
        }

        if (procedure.hasPerformed()) {
            Type performed = procedure.getPerformed();
            if (performed instanceof DateTimeType) {
                result.put("date", DATE_FORMAT.format(((DateTimeType) performed).getValue()));
            } else if (performed instanceof Period) {
                Period period = (Period) performed;
                if (period.hasStart()) {
                    result.put("date", DATE_FORMAT.format(period.getStart()));
                }
            } else if (performed instanceof DateType) {
                result.put("date", DATE_FORMAT.format(((DateType) performed).getValue()));
            }
        }

        if (!procedure.getPerformer().isEmpty()) {
            Procedure.ProcedurePerformerComponent performer = procedure.getPerformer().get(0);
            if (performer.hasActor() && performer.getActor().hasDisplay()) {
                result.put("performer", performer.getActor().getDisplay());
            }
        }

        return result;
    }

    private static Map<String, Object> mapFamilyMemberHistory(FamilyMemberHistory history) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (history.hasName()) {
            result.put("name", history.getName());
        }

        if (history.hasCondition() && !history.getCondition().isEmpty()) {
            List<Map<String, String>> conditions = new ArrayList<>();
            for (FamilyMemberHistory.FamilyMemberHistoryConditionComponent condition : history.getCondition()) {
                Map<String, String> condMap = new LinkedHashMap<>();
                if (!condition.getCode().getCoding().isEmpty()) {
                    Coding coding = condition.getCode().getCoding().get(0);
                    condMap.put("code", coding.getCode());
                    condMap.put("display", coding.getDisplay());
                }
                conditions.add(condMap);
            }
            result.put("condition", conditions);
        }

        if (history.hasGender()) {
            result.put("sex", history.getGender().toCode());
        }

        Object relObj = history.getRelationship();
        if (relObj instanceof Enum) {
            @SuppressWarnings("unchecked")
            Enum<?> rel = (Enum<?>) relObj;
            result.put("relationship", rel.name().toLowerCase());
        }

        return result;
    }

    private static Map<String, Object> mapReferralRequest(ReferralRequest referral) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!referral.getServiceRequested().isEmpty()) {
            CodeableConcept service = referral.getServiceRequested().get(0);
            result.put("serviceRequested", service.getText());
        }

        if (referral.hasStatus()) {
            result.put("status", referral.getStatus().toCode());
        }

        if (referral.hasPriority()) {
            result.put("priority", referral.getPriority().toCode());
        }

        if (referral.hasAuthoredOn()) {
            result.put("requestDate", DATE_FORMAT.format(referral.getAuthoredOn()));
        }

        return result;
    }

    private static Map<String, Object> mapAlert(Flag alert) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (alert.hasText()) {
            result.put("text", alert.getText());
        }

        if (!alert.getCode().getCoding().isEmpty()) {
            Coding coding = alert.getCode().getCoding().get(0);
            result.put("category", coding.getCode());
            result.put("display", coding.getDisplay());
        }

        return result;
    }

    private static Map<String, Map<String, String>> mapOrganization(Organization organization) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();

        String orgKey = "unknown";
        for (Identifier identifier : organization.getIdentifier()) {
            if (identifier.hasSystem() && identifier.getValue() != null) {
                orgKey = identifier.getValue();
                break;
            }
        }

        Map<String, String> orgData = new LinkedHashMap<>();
        if (organization.hasName()) {
            orgData.put("name", organization.getName());
        }
        if (!organization.getAddress().isEmpty()) {
            Address address = organization.getAddress().get(0);
            if (address.hasPostalCode()) {
                orgData.put("postcode", address.getPostalCode());
            }
        }

        result.put(orgKey, orgData);
        return result;
    }

    private static Map<String, Map<String, String>> mapPractitioner(Practitioner practitioner) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();

        String practKey = practitioner.getId() != null ? practitioner.getId() : "unknown";

        Map<String, String> practData = new LinkedHashMap<>();
        if (!practitioner.getName().isEmpty()) {
            HumanName name = practitioner.getName().get(0);
            StringBuilder fullName = new StringBuilder();
            if (name.hasPrefix()) {
                List<String> prefixes = new ArrayList<>();
                for (StringType s : name.getPrefix()) { prefixes.add(s.getValue()); }
                fullName.append(String.join(" ", prefixes)).append(" ");
            }
            if (name.hasGiven()) {
                List<String> givenNames = new ArrayList<>();
                for (StringType s : name.getGiven()) { givenNames.add(s.getValue()); }
                fullName.append(String.join(" ", givenNames)).append(" ");
            }
            if (name.hasFamily()) {
                fullName.append(name.getFamily());
            }
            practData.put("name", fullName.toString().trim());
        }

        result.put(practKey, practData);
        return result;
    }

    private static Map<String, Object> filterBySelect(Map<String, Object> original, List<String> selectProperties) {
        if (selectProperties.isEmpty()) {
            return original;
        }

        Set<String> selectSet = new HashSet<>(selectProperties);
        Map<String, Object> filtered = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : original.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("__")) {
                filtered.put(key, entry.getValue());
            } else if (selectSet.contains(key)) {
                filtered.put(key, entry.getValue());
            }
        }

        return filtered;
    }
}
