package br.com.fiap.PaperTrail.auth;

import br.com.fiap.PaperTrail.auth.entity.User;
import br.com.fiap.PaperTrail.auth.repository.UserRepository;
import br.com.fiap.PaperTrail.support.JwtTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest extends JwtTestSupport {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwords;
    @Autowired private TokenService tokens;
    @Autowired private JwtEncoder encoder;
    @Autowired private JwtDecoder decoder;
    @Autowired private ObjectMapper json;

    @Test
    void registrationNormalizesEmailHashesPasswordAndCreatesOnlyCitizens() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"  Test Citizen  ","email":" Citizen@Example.invalid ","password":"password123","role":"MANAGER"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Test Citizen"))
                .andExpect(jsonPath("$.email").value("citizen@example.invalid"))
                .andExpect(jsonPath("$.role").value("CITIZEN"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        User saved = users.findByEmail("citizen@example.invalid").orElseThrow();
        assertEquals(User.Role.CITIZEN, saved.getRoleName());
        assertNotEquals("password123", saved.getPasswordHash());
        assertTrue(passwords.matches("password123", saved.getPasswordHash()));
    }

    @Test
    void duplicateEmailIsCaseInsensitive() throws Exception {
        account("citizen@example.invalid", User.Role.CITIZEN);
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Citizen","email":"CITIZEN@example.invalid","password":"password123"}
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Email is already registered."));
        assertEquals(1, users.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\" \",\"email\":\"citizen@example.invalid\",\"password\":\"password123\"}",
            "{\"name\":\"Citizen\",\"email\":\"invalid\",\"password\":\"password123\"}",
            "{\"name\":\"Citizen\",\"email\":\"citizen@example.invalid\",\"password\":\"short\"}",
            "{\"name\":\"Citizen\",\"email\":\"citizen@example.invalid\"}",
            "{\"name\":\"Citizen\",\"email\":\"citizen@example.invalid\",\"password\":\"        \"}"
    })
    void invalidRegistrationReturnsSafeValidationErrors(String body) throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").exists())
                .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist());
        assertEquals(0, users.count());
    }

    @Test
    void unicodePasswordCannotExceedBcryptByteLimit() throws Exception {
        var body = json.writeValueAsString(java.util.Map.of("name", "Citizen", "email", "citizen@example.invalid",
                "password", "á".repeat(37)));
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(0, users.count());
    }

    @Test
    void malformedJsonDoesNotExposeTheRequestBody() throws Exception {
        var result = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"private-test-value\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid JSON request."))
                .andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("private-test-value"));
    }

    @Test
    void loginIssuesTheSharedJwtContractAndCanAccessMe() throws Exception {
        User user = account("citizen@example.invalid", User.Role.CITIZEN);
        var result = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":" CITIZEN@EXAMPLE.INVALID ","password":"password123"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andReturn();
        String token = json.readTree(result.getResponse().getContentAsString()).get("token").asString();
        var jwt = decoder.decode(token);
        assertEquals("RS256", jwt.getHeaders().get("alg"));
        assertEquals("papertrail", jwt.getClaimAsString("iss"));
        assertEquals(List.of("papertrail-api"), jwt.getAudience());
        assertEquals(user.getId().toString(), jwt.getSubject());
        assertEquals(List.of("CITIZEN"), jwt.getClaimAsStringList("roles"));
        assertEquals(1800, jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond());
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"citizen@example.invalid", "missing@example.invalid"})
    void wrongPasswordAndUnknownUserReturnTheSame401(String email) throws Exception {
        account("citizen@example.invalid", User.Role.CITIZEN);
        String body = json.writeValueAsString(java.util.Map.of("email", email, "password", "wrong-password"));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void missingAndMalformedTokensReturn401() throws Exception {
        mvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/auth/me").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void citizenCannotAccessManagerEndpointsButManagerCan() throws Exception {
        User citizen = account("citizen@example.invalid", User.Role.CITIZEN);
        User manager = account("manager@example.invalid", User.Role.MANAGER);
        mvc.perform(get("/services/1/guidance-chunks").header("Authorization", "Bearer " + tokens.generateToken(citizen.getEmail())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/services/1/guidance-chunks").header("Authorization", "Bearer " + tokens.generateToken(manager.getEmail())))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "issuer", "audience", "role", "subject", "no-expiration", "deleted-user"})
    void invalidSignedTokensAreRejected(String invalidClaim) throws Exception {
        User user = account("citizen@example.invalid", User.Role.CITIZEN);
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(invalidClaim.equals("issuer") ? "other-project" : TokenService.ISSUER)
                .audience(List.of(invalidClaim.equals("audience") ? "other-api" : TokenService.AUDIENCE))
                .subject(invalidClaim.equals("subject") ? "not-an-id" : user.getId().toString())
                .issuedAt(now.minusSeconds(600))
                .claim("roles", List.of(invalidClaim.equals("role") ? "MANAGER" : "CITIZEN"));
        if (!invalidClaim.equals("no-expiration")) {
            claims.expiresAt(invalidClaim.equals("expired") ? now.minusSeconds(300) : now.plusSeconds(1800));
        }
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build())).getTokenValue();
        if (invalidClaim.equals("deleted-user")) {
            users.delete(user);
            users.flush();
        }
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void modifiedSignatureIsRejected() throws Exception {
        User user = account("citizen@example.invalid", User.Role.CITIZEN);
        String token = tokens.generateToken(user.getEmail());
        int signatureStart = token.lastIndexOf('.') + 1;
        char changed = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signatureStart) + changed + token.substring(signatureStart + 1);
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleChangesInvalidateExistingTokens() throws Exception {
        User user = account("manager@example.invalid", User.Role.MANAGER);
        String token = tokens.generateToken(user.getEmail());
        user.setRoleName(User.Role.CITIZEN);
        users.saveAndFlush(user);
        mvc.perform(get("/services/1/guidance-chunks").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerIsPublicAndDeclaresBearerAuthentication() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/auth/register'].post.security").isEmpty());
    }

    private User account(String email, User.Role role) {
        var user = new User();
        user.setName("Test " + role);
        user.setEmail(email);
        user.setPasswordHash(passwords.encode("password123"));
        user.setRoleName(role);
        return users.saveAndFlush(user);
    }
}
