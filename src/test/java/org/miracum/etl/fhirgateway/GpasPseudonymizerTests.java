package org.miracum.etl.fhirgateway;

import org.emau.icmvc.ganimed.ttp.psn.PSNManager;
import org.emau.icmvc.ganimed.ttp.psn.exceptions.DBException;
import org.emau.icmvc.ganimed.ttp.psn.exceptions.InvalidGeneratorException;
import org.emau.icmvc.ganimed.ttp.psn.exceptions.InvalidParameterException;
import org.emau.icmvc.ganimed.ttp.psn.exceptions.UnknownDomainException;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.miracum.etl.fhirgateway.processors.GpasPseudonymizer;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@RunWith(MockitoJUnitRunner.class)
public class GpasPseudonymizerTests {

    @Mock
    private PSNManager psnManager;

    private FhirSystemsConfig fhirSystems;
    private GpasPseudonymizer sut;

    @Before
    public void setUp() {
        fhirSystems = new FhirSystemsConfig() {{
            setEncounterId("http://fhir.example.com/encounter-id");
            setPatientId("http://fhir.example.com/patient-id");
        }};

        sut = new GpasPseudonymizer(psnManager, fhirSystems);
    }

    @Test
    public void process_patient_shouldPseudonymizeId() throws DBException, InvalidParameterException, UnknownDomainException, InvalidGeneratorException {
        var patient = new Patient() {{
            setId("secretPid");
        }};

        when(psnManager.getOrCreatePseudonymForList(any(), any()))
                .thenReturn(Map.of("secretPid", "hiddenPid"));

        var result = sut.process(List.of(patient));

        assertThat(result).size().isOne();

        var pseudonymizedPatient = (Patient) result.get(0);
        assertThat(pseudonymizedPatient.getId()).isEqualTo("hiddenPid");
    }

    @Test
    public void process_encounter_shouldPseudonymizeIdAndSubjectReference() throws DBException, InvalidParameterException, UnknownDomainException, InvalidGeneratorException {
        var encounter = new Encounter() {{
            setId("secretCid");
            setSubject(new Reference("Patient/secretPid"));
        }};

        when(psnManager.getOrCreatePseudonymForList(any(), any()))
                .thenReturn(Map.of("secretPid", "hiddenPid", "secretCid", "hiddenCid"));

        var result = sut.process(List.of(encounter));

        assertThat(result).size().isOne();

        var pseudonymizedEncounter = (Encounter) result.get(0);
        assertThat(pseudonymizedEncounter.getId()).isEqualTo("hiddenCid");
        assertThat(pseudonymizedEncounter.getSubject().getReference()).isEqualTo("Patient/hiddenPid");
    }

    @Test
    public void process_patientWithIdentifier_shouldPseudonymizeIdentifierList() throws DBException, InvalidParameterException, UnknownDomainException, InvalidGeneratorException {
        var patient = new Patient() {{
            setId("secretPid");
            addIdentifier()
                    .setSystem(fhirSystems.getPatientId())
                    .setValue("secretPid");
        }};

        when(psnManager.getOrCreatePseudonymForList(any(), any()))
                .thenReturn(Map.of("secretPid", "hiddenPid"));

        var result = sut.process(List.of(patient));

        assertThat(result).size().isOne();

        var pseudonymizedPatient = (Patient) result.get(0);
        assertThat(pseudonymizedPatient.getIdentifierFirstRep().getValue()).isEqualTo("hiddenPid");
    }
}