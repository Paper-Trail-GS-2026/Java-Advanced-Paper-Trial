package br.com.fiap.PaperTrail.catalog;

import br.com.fiap.PaperTrail.category.entity.Category;
import br.com.fiap.PaperTrail.category.repository.CategoryRepository;
import br.com.fiap.PaperTrail.service.entity.PublicService;
import br.com.fiap.PaperTrail.service.event.ServiceVersionChanged;
import br.com.fiap.PaperTrail.service.repository.PublicServiceRepository;
import br.com.fiap.PaperTrail.service.service.CatalogVersionService;
import br.com.fiap.PaperTrail.support.JwtTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(CatalogVersionTest.Changes.class)
class CatalogVersionTest extends JwtTestSupport {
    @Autowired private CategoryRepository categories;
    @Autowired private PublicServiceRepository services;
    @Autowired private CatalogVersionService versions;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Changes changes;
    private TransactionTemplate transactions;
    private Long categoryId;
    private Long serviceId;

    static class Changes {
        final List<ServiceVersionChanged> events = new CopyOnWriteArrayList<>();

        @TransactionalEventListener
        void changed(ServiceVersionChanged event) {
            events.add(event);
        }
    }

    @BeforeEach
    void fixtures() {
        transactions = new TransactionTemplate(transactionManager);
        serviceId = transactions.execute(status -> {
            var category = new Category();
            category.setName("Version test " + UUID.randomUUID());
            categoryId = categories.saveAndFlush(category).getId();
            var service = new PublicService();
            service.setCategory(category);
            service.setTitle("Version test");
            service.setDescription("Fictitious data");
            service.setCityName("Test city");
            return services.saveAndFlush(service).getId();
        });
        changes.events.clear();
    }

    @AfterEach
    void cleanUp() {
        transactions.executeWithoutResult(status -> {
            services.deleteById(serviceId);
            services.flush();
            categories.deleteById(categoryId);
        });
    }

    @Test
    void versionChangeIsAvailableToListenersOnlyAfterCommit() {
        transactions.executeWithoutResult(status -> {
            assertEquals(2L, versions.incrementVersion(serviceId).getVersion());
            assertTrue(changes.events.isEmpty());
        });
        assertEquals(2, versions.currentVersion(serviceId));
        assertEquals(1, changes.events.size());
        var event = changes.events.get(0);
        assertEquals(serviceId, event.serviceId());
        assertEquals(2L, event.version());
        assertNotNull(event.eventId());
        assertNotNull(event.occurredAt());
    }

    @Test
    void rollbackPreservesVersionAndDoesNotNotifyAfterCommitListeners() {
        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
            versions.incrementVersion(serviceId);
            throw new IllegalStateException("Rollback test");
        }));
        assertEquals(1, versions.currentVersion(serviceId));
        assertTrue(changes.events.isEmpty());
    }

    @Test
    void incrementRequiresTheCallersTransaction() {
        assertThrows(IllegalTransactionStateException.class, () -> versions.incrementVersion(serviceId));
        assertEquals(1, versions.currentVersion(serviceId));
    }

    @Test
    void versionLimitCannotOverflowTheOracleColumn() {
        transactions.executeWithoutResult(status -> services.findById(serviceId).orElseThrow().setVersion(999999999999999999L));
        var exception = assertThrows(ResponseStatusException.class,
                () -> transactions.execute(status -> versions.incrementVersion(serviceId)));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(999999999999999999L, versions.currentVersion(serviceId));
        assertTrue(changes.events.isEmpty());
    }

    @Test
    void missingServiceReturns404() {
        var exception = assertThrows(ResponseStatusException.class, () -> versions.currentVersion(999999999L));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void concurrentUpdateDoesNotLoseAnIncrementEvenWithAPreviouslyLoadedEntity() throws Exception {
        var firstRead = new CountDownLatch(1);
        var secondCommitted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> transactions.execute(status -> {
                assertEquals(1L, services.findById(serviceId).orElseThrow().getVersion());
                firstRead.countDown();
                await(secondCommitted);
                return versions.incrementVersion(serviceId).getVersion();
            }));
            var second = executor.submit(() -> {
                await(firstRead);
                try {
                    return transactions.execute(status -> versions.incrementVersion(serviceId).getVersion());
                } finally {
                    secondCommitted.countDown();
                }
            });
            assertEquals(2L, second.get(15, TimeUnit.SECONDS));
            assertEquals(3L, first.get(15, TimeUnit.SECONDS));
            assertEquals(3, versions.currentVersion(serviceId));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent test timed out.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
