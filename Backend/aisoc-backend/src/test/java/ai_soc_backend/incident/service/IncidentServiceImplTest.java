package ai_soc_backend.incident.service;

import ai_soc_backend.incident.repository.IncidentRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IncidentServiceImplTest {

    @Test
    void throwsTypedNotFoundExceptionWhenIncidentDoesNotExist() {
        IncidentRepository repository = mock(IncidentRepository.class);
        when(repository.findByIncidentId("missing")).thenReturn(Optional.empty());
        IncidentServiceImpl service = new IncidentServiceImpl(repository);

        assertThrows(IncidentNotFoundException.class, () -> service.getIncidentById("missing"));
    }
}
