package br.com.fiap.PaperTrail.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public abstract class JwtTestSupport {
    protected static final KeyPair KEYS;
    private static final Path KEY_DIRECTORY;

    static {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KEYS = generator.generateKeyPair();
            KEY_DIRECTORY = Files.createTempDirectory("papertrail-test-keys-");
            KEY_DIRECTORY.toFile().deleteOnExit();
            writeKey("public.pem", "PUBLIC KEY", KEYS.getPublic().getEncoded());
            writeKey("private.pem", "PRIVATE KEY", KEYS.getPrivate().getEncoded());
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void writeKey(String fileName, String label, byte[] bytes) throws Exception {
        var path = KEY_DIRECTORY.resolve(fileName);
        String content = "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes)
                + "\n-----END " + label + "-----\n";
        Files.writeString(path, content, StandardCharsets.US_ASCII);
        path.toFile().deleteOnExit();
    }

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("papertrail.jwt.public-key", () -> KEY_DIRECTORY.resolve("public.pem").toUri().toString());
        registry.add("papertrail.jwt.private-key", () -> KEY_DIRECTORY.resolve("private.pem").toUri().toString());
    }
}
