# getFHIRRecordAPI - Agent Guide

## Project Overview
Java 8 Maven multi-module project that exposes UK NHS patient clinical data as FHIR DSTU3 Bundles. Part of the Endeavour Health Discovery Data Service.

**GroupId:** `org.endeavourhealth.getFHIRRecordAPI`  
**Version:** `1.0-SNAPSHOT`  
**Packaging:** Parent POM (modules: common/JAR, API/WAR)

## Build & Run Commands

```bash
# Build entire project
mvn -B package

# Build specific module
cd API && mvn -B package
cd ../common && mvn -B package

# Run tests
mvn test

# SonarQube analysis
mvn sonar:sonar
```

## Project Structure

```
getFHIRRecordAPI/
├── pom.xml                          # Parent POM (dependency management, plugins)
├── settings.xml                     # Maven settings (Artifactory credentials)
├── API/                             # WAR module - REST API layer
│   ├── pom.xml                      # Dependencies: Jersey, HAPI FHIR 3.8.0, Jackson, Keycloak
│   └── src/main/java/org/endeavourhealth/getFHIRRecordAPI/api/
│       ├── endpoints/               # REST controllers
│       │   ├── CareRecordEndpoint.java      # POST /patient/$getstructuredrecord
│       │   └── PalantirObjectEndpoint.java  # GET /api/v2/ontologies/nhs/objects/patient/{primaryKey}
│       ├── process/                 # Business logic & FHIR processing
│       │   ├── FhirApi.java               # Core orchestrator (875 lines) - main entry point
│       │   ├── FhirUtil.java              # Utilities: config reading, param extraction, test anonymization
│       │   ├── FhirBundleParams.java      # Parameter holder for bundle generation
│       │   ├── FhirResourceUtil.java      # Resource utility (planned features)
│       │   └── PalantirBundleMapper.java  # Maps FHIR Bundle to Palantir ontology format
│       ├── resources/               # FHIR resource builders (25 files)
│       │   ├── Patient.java                   # CareConnect-Patient-1 profile
│       │   ├── Condition.java                 # CareConnect-ProblemHeader-Condition-1
│       │   ├── Observation.java               # CareConnect-GPC-Observation-1
│       │   ├── AllergyIntolerance.java        # CareConnect-AllergyIntolerance-1
│       │   ├── MedicationStatement.java       # Medication + MedicationStatement
│       │   ├── Encounter.java
│       │   ├── Immunization.java
│       │   ├── Appointment.java
│       │   ├── Procedure.java
│       │   ├── Practitioner.java              # CareConnect-Practitioner-1
│       │   ├── Organization.java
│       │   ├── FamilyMemberHistory.java       # CareConnect-FamilyMemberHistory-1
│       │   ├── ReferralRequest.java           # CareConnect-ReferralRequest-1
│       │   ├── Alert.java                     # FHIR Flag resource
│       │   └── DiagnosticReport.java
│       └── models/                        # Request/response DTOs
│           ├── Params.java                  # Root request object (FHIR Parameters)
│           ├── Parameter.java               # Individual parameter with parts
│           ├── ConfigDetails.java           # DSM subscriber config (GP/COMMUNITY/ACUTE)
│           ├── Meta.java, Coding.java, Identifier.java
├── common/                          # JAR module - shared models & DAL
│   ├── pom.xml                      # Dependencies: configmanager, cache, core, utility, trove4j
│   └── src/main/java/org/endeavourhealth/getFHIRRecordAPI/common/
│       ├── constants/
│       │   └── ResourceConstants.java     # FHIR profile URIs, system IDs (CareConnect standards)
│       ├── dal/                       # Data access layer
│       │   ├── JDBCDAL.java               # Main DAL (1376 lines) - JDBC via EnterpriseConnector
│       │   ├── DALHelper.java             # JDBC parameter helpers
│       │   ├── S3Filer.java               # AWS S3 HL7 message storage
│       │   └── QueryConstants.java        # Dynamic SQL for speciality lookups
│       └── models/                    # Domain model classes
│           ├── PatientFull.java
│           ├── ObservationFull.java
│           ├── ConditionFull.java
│           ├── AllergyFull.java
│           ├── EncounterFull.java
│           ├── MedicationStatementFull.java
│           ├── PractitionerFull.java
│           ├── OrganizationFull.java
│           └── TelecomFull.java
├── database/                          # SQL scripts
│   ├── CreateTables/                  # Table DDL (code_category, code_category_values)
│   └── PopulateCodeCategories/        # 49 SQL files populating clinical coding data
├── aws_scripts/                       # Deployment scripts (build.sh, cleanup)
├── appspec.yml                        # AWS CodeDeploy spec -> Tomcat /opt/tomcat/webapps/fhirAPI
├── buildspec.yml                      # AWS CodeBuild spec
└── README.md
```

## API Endpoints

### 1. Get Structured Record (Primary Endpoint)
- **Path:** `POST /patient/$getstructuredrecord`
- **Consumes/Produces:** `application/json`
- **Auth:** `user_id` HTTP header required
- **Request Body:** FHIR Parameters with:
  - `patientNHSNumber` (ValueIdentifier, system: `https://fhir.hl7.org.uk/Id/nhs-number`)
  - `patientDOB` (optional)
  - `demographicsOnly` (boolean part param)
  - `includeInactivePatients` (boolean part param)
- **Response:** FHIR Bundle (DSTU3 JSON) containing Patient + clinical resources

### 2. Palantir Object Lookup
- **Path:** `GET /api/v2/ontologies/nhs/objects/patient/{primaryKey}`
- **Auth:** `user_id` HTTP header required
- **Query Params:** `select` (property filter), `excludeRid` (omit __rid fields)
- **Response:** Palantir ontology format map

## Architecture Notes

### Data Flow
1. Request arrives at endpoint controller
2. `FhirApi.handleRequest()` orchestrates: reads DSM subscriber configs, extracts params
3. Iterates over database types: GP -> COMMUNITY -> ACUTE (reordered by `FhirBundleParams.reorderConfigList()`)
4. For each subscriber DB, queries patient data via `JDBCDAL` using EndeavourHealth EnterpriseConnector
5. Builds FHIR DSTU3 resources using HAPI FHIR library
6. Aggregates all resources into single Bundle with organization ODS codes in metadata

### Database Types
- **GP** - General Practice / primary care
- **COMMUNITY** - Community health services
- **ACUTE** - Hospital/acute care (additional fields: ward codes, speciality)

### Key Business Rules
- Vital signs observations limited to max 3 per code within a year (`FhirUtil`)
- Test mode anonymizes patient data using test NHS number mapping
- Resources tagged with organization metadata (ODS codes)
- ACUTE databases include Alerts and Procedures; all types get Conditions, Allergies, Medications, Encounters, Immunizations, Family History, Referrals

### FHIR Standards
All resources use UK NHS CareConnect STU3 profiles:
- Patient: `CareConnect-Patient-1`
- Condition: `CareConnect-ProblemHeader-Condition-1`
- Observation: `CareConnect-GPC-Observation-1`
- AllergyIntolerance: `CareConnect-AllergyIntolerance-1`
- Practitioner: `CareConnect-Practitioner-1`
- Bundle system ID: `CareConnect-StructuredRecord-Bundle`

### Code System Mapping (JDBCDAL)
Maps concept IDs to code system URLs for SNOMED, READ v2/v3, ICD-10, OPCS-4, EMIS local, TPP local.

## Key Dependencies
| Library | Version | Purpose |
|---------|---------|---------|
| hapi-fhir-structures-dstu3 | 3.8.0 | FHIR DSTU3 data structures |
| hapi-fhir-client | 3.8.0 | FHIR client |
| jersey-container-servlet | - | JAX-RS / REST container |
| jackson-jaxrs-json-provider | - | JSON serialization |
| keycloak-core/common | - | Authentication (provided scope) |
| aws-lambda-java-core | 1.2.0 | AWS Lambda integration |
| cryptolib | - | Pseudonymization |

## Deployment
- **Build:** AWS CodeBuild (`buildspec.yml`) -> `mvn -B package`
- **Deploy:** AWS CodeDeploy (`appspec.yml`) -> WAR to `/opt/tomcat/webapps/fhirAPI`
- **Region:** eu-west-2 (London)
- **S3 buckets:** `endeavour-codebuild` (artifacts/badges), `hl7-nwl-dev` (HL7 messages)

## Testing
```bash
mvn test
```
Uses JUnit 4 + Mockito. Tests in `API/src/test/java/`.

## Important Files to Know
- `FhirApi.java` - Core orchestrator, all business logic lives here
- `JDBCDAL.java` - All database queries, ~1376 lines
- `ResourceConstants.java` - FHIR profile URIs and system identifiers
- `CareRecordEndpoint.java` - Main REST endpoint entry point
- `PalantirBundleMapper.java` - FHIR to Palantir transformation logic
