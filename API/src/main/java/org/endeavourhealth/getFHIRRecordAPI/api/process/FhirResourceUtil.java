package org.endeavourhealth.getFHIRRecordAPI.api.process;

public class FhirResourceUtil {

     /*  private void addDiagnosticReportToBundle(List<Long> patientIds, JDBCDAL viewerDAL, String dbtype) throws Exception {
        // Observation resource
        List<DiagnosticReportFull> diagnosticReportFullList = viewerDAL.getDiagnosticReportFullList(patientIds);

        if (diagnosticReportFullList.size() > 0) {
            org.hl7.fhir.dstu3.model.ListResource observationListResource = ObservationList.getObservationResource();
            observationListResource.setSubject(new Reference(patientResource));
            if (!diagnosticReportFullList.isEmpty()) {
                for (DiagnosticReportFull diagnosticReportFull : diagnosticReportFullList) {
                    String observationId = String.valueOf(diagnosticReportFull.getId());
                    if (!observationIds.contains(observationId)) {
                        observationIds.add(observationId);
                        DiagnosticReport diagnosticFhir = new DiagnosticReport(diagnosticReportFull, viewerDAL);
                        org.hl7.fhir.dstu3.model.DiagnosticReport observationResource = diagnosticFhir.getDiagnosticReport();
                        observationResource.getMeta().addTag(patientCodingMap.get((diagnosticReportFull.getPatientId())));
                        // observationResource.setPerformer(Arrays.asList(new Reference(getPractitionerRoleResource(new Long(observationFull.getPractitionerId()), observationFull.getOrganizationId(),viewerDAL))));
                        observationResource.setSubject(new Reference(patientResource));
                        if (diagnosticReportFull.getEncounterId() != 0) {
                            observationResource.setContext(new Reference(getEncounterFhirObj(diagnosticReportFull.getEncounterId(), viewerDAL,dbtype)));
                        }
                        bundle.addEntry().setResource(observationResource);
                        observationListResource.addEntry().setItem(new Reference(observationResource));
                    }
                }
            }
            bundle.addEntry().setResource(observationListResource);
        }
    }
      private void addLocationToBundle(String organizationId, JDBCDAL viewerDAL) throws Exception {
        if (StringUtils.isNotEmpty(organizationId)) {
            LocationFull locationFull = viewerDAL.getLocation(Long.parseLong(organizationId));
            if (locationFull.getId() != 0) {
                org.hl7.fhir.dstu3.model.Location locationModel = Location.getLocationResource(locationFull);

                locationModel.setManagingOrganization(new Reference(organizationId));
                bundle.addEntry().setResource(locationModel);
            }
        }
    }
    */

    /*  private org.hl7.fhir.dstu3.model.PractitionerRole getPractitionerRoleResource(Long practitionerId, Long organizationID, JDBCDAL viewerDAL) throws Exception {
        if (practitionerRoleResourcesMap.get(practitionerId) != null) {
            return (org.hl7.fhir.dstu3.model.PractitionerRole) practitionerRoleResourcesMap.get(practitionerId);
        } else {
            PractitionerFull practitionerResult = viewerDAL.getPractitionerFull(practitionerId);
            if (practitionerResult == null) {
                return null;
            }
            org.hl7.fhir.dstu3.model.Practitioner practitionerResource = getPractitionerResource(practitionerId, viewerDAL);

            org.hl7.fhir.dstu3.model.PractitionerRole practitionerRoleResource = PractitionerRole.getPractitionerRoleResource(practitionerResult);
            practitionerRoleResource.setPractitioner(new Reference(practitionerResource));
            practitionerRoleResource.setOrganization(new Reference(getOrganizationFhirObj(organizationID, viewerDAL)));
            practitionerRoleResourcesMap.put(practitionerId, practitionerRoleResource);
            return practitionerRoleResource;
        }
    }*/

      /*
        private void addEpisodeOfCareToBundle(List<Long> patientIds, JDBCDAL viewerDAL) throws Exception {
            List<EpisodeOfCareFull> episodeOfCareFullList = viewerDAL.getEpisodeOfCareFull(patientIds);
            Map<Long, List<EpisodeOfCareFull>> episodeOfCareOrganizationMap = getOrganizationList(episodeOfCareFullList);
            if (!episodeOfCareOrganizationMap.isEmpty()) {
                for (Map.Entry<Long, List<EpisodeOfCareFull>> episodeOfCareList : episodeOfCareOrganizationMap.entrySet()) {
                    org.hl7.fhir.dstu3.model.EpisodeOfCare episodeOfCare = EpisodeOfCare.getEpisodeOfCareResource(episodeOfCareList.getValue());
                    episodeOfCare.getMeta().addTag(patientCodingMap.get(((episodeOfCareList.getValue()).get(0).getPatientId())));
                    episodeOfCare.setPatient(new Reference(patientResource));
                    if (episodeOfCareFullList.get(0).getOrganizationId() != 0) {
                        episodeOfCare.setManagingOrganization(new Reference(getOrganizationFhirObj(episodeOfCareFullList.get(0).getOrganizationId(), viewerDAL)));
                    }
                    if (episodeOfCareFullList.get(0).getPractitionerId() != 0) {
                        org.hl7.fhir.dstu3.model.Practitioner practitioner = getPractitionerResource(episodeOfCareFullList.get(0).getPractitionerId(), viewerDAL);
                        if (practitioner != null) {
                            episodeOfCare.setCareManager(new Reference(practitioner));
                        }
                    }
                    episodeOfCareResourceMap.put(getEpisodeOfCareResource(episodeOfCareFullList), episodeOfCare);
                    bundle.addEntry().setResource(episodeOfCare);
                }
            }
        }

    private String getEpisodeOfCareResource(List<EpisodeOfCareFull> episodeOfCareFullList) {
        return episodeOfCareFullList.stream().map(p -> String.valueOf(p.getId()))
                .collect(Collectors.joining(","));

    }

    private Map<Long, List<EpisodeOfCareFull>> getOrganizationList(List<EpisodeOfCareFull> episodeOfCareFullList) {
        Map<Long, List<EpisodeOfCareFull>> episodeOfCareOrganizationList = new HashMap<>();
        episodeOfCareFullList.forEach(episodeOfCareFull -> {
            if (episodeOfCareOrganizationList.containsKey(episodeOfCareFull.getOrganizationId())) {
                episodeOfCareOrganizationList.get(episodeOfCareFull.getOrganizationId()).add(episodeOfCareFull);
            } else {
                episodeOfCareOrganizationList.put(episodeOfCareFull.getOrganizationId(), new ArrayList<>(Arrays.asList(episodeOfCareFull)));
            }
        });
        return episodeOfCareOrganizationList;
    }
    */
}
