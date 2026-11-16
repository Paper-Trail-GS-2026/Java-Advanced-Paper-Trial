package br.com.fiap.PaperTrail.auth;

import br.com.fiap.PaperTrail.auth.dto.RegisterRequest;
import br.com.fiap.PaperTrail.auth.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final AuthService service = new AuthService(users, passwords);
    private final RegisterRequest request = new RegisterRequest("Citizen", "citizen@example.invalid", "password123");

    @Test
    void concurrentEmailConflictReturns409() {
        when(passwords.encode(any())).thenReturn("test-hash");
        var cause = new ConstraintViolationException("duplicate", new SQLException("test"), "RM_TEST.UQ_PT_USER_EMAIL");
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate", cause));
        var exception = assertThrows(ResponseStatusException.class, () -> service.register(request));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void unrelatedDatabaseErrorsAreNotReportedAsDuplicateEmails() {
        when(passwords.encode(any())).thenReturn("test-hash");
        var failure = new DataIntegrityViolationException("other constraint");
        when(users.saveAndFlush(any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class, () -> service.register(request)));
    }
}
