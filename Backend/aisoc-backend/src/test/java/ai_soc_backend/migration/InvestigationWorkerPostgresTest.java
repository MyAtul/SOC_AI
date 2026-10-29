package ai_soc_backend.migration;

import ai_soc_backend.incident.dto.AIInvestigationResponse;
import ai_soc_backend.incident.entity.Incident;
import ai_soc_backend.incident.entity.IncidentInvestigationWork;
import ai_soc_backend.incident.repository.AIInvestigationRepository;
import ai_soc_backend.incident.repository.IncidentInvestigationWorkRepository;
import ai_soc_backend.incident.repository.IncidentRepository;
import ai_soc_backend.incident.service.InvestigationWorkClaim;
import ai_soc_backend.incident.service.InvestigationWorkClaimService;
import ai_soc_backend.incident.service.InvestigationWorkFailureService;
import ai_soc_backend.incident.service.InvestigationWorkFinalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="aisoc.phase2.postgres-test", matches="true")
@SpringBootTest(classes=InvestigationWorkerPostgresTest.TestApplication.class)
class InvestigationWorkerPostgresTest {
    private static final String URL="jdbc:postgresql://127.0.0.1:55432/aisoc_test";
    private static final String USER="aisoc_test";
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        String password=System.getenv("AISOC_V2_TEST_PASSWORD");
        if(password==null||password.isBlank()) throw new IllegalStateException("AISOC_V2_TEST_PASSWORD required");
        try(var c=DriverManager.getConnection(URL,USER,password); var s=c.createStatement();
            var r=s.executeQuery("SELECT current_database(),session_user,current_setting('server_version')")) {
            if(!r.next()||!"aisoc_test".equals(r.getString(1))||!USER.equals(r.getString(2))||!r.getString(3).startsWith("18.3"))
                throw new IllegalStateException("Refusing non-disposable database target");
        } catch(Exception e) { throw new IllegalStateException("Could not verify disposable database",e); }
        p.add("spring.datasource.url",()->URL); p.add("spring.datasource.username",()->USER);
        p.add("spring.datasource.password",()->password); p.add("spring.flyway.enabled",()->"false");
        p.add("spring.jpa.hibernate.ddl-auto",()->"none"); p.add("spring.jpa.open-in-view",()->"false");
    }
    @org.springframework.beans.factory.annotation.Autowired IncidentRepository incidents;
    @org.springframework.beans.factory.annotation.Autowired IncidentInvestigationWorkRepository works;
    @org.springframework.beans.factory.annotation.Autowired AIInvestigationRepository results;
    @org.springframework.beans.factory.annotation.Autowired InvestigationWorkClaimService claims;
    @org.springframework.beans.factory.annotation.Autowired InvestigationWorkFailureService failures;
    @org.springframework.beans.factory.annotation.Autowired InvestigationWorkFinalizer finalizer;
    @org.springframework.beans.factory.annotation.Autowired JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired PlatformTransactionManager tx;

    @Test void finalResultAndWorkCompletionCommitTogetherAndDuplicateResultIsNotInserted() {
        assertNoEligibleWorkOutsideThisTest();
        String id="INV-WORK-"+UUID.randomUUID(); Long pk=null;
        try {
            Incident incident=incidents.saveAndFlush(new Incident(id,"test","HIGH","OPEN","host","test", LocalDateTime.now()));
            pk=incident.getId();
            works.saveAndFlush(new IncidentInvestigationWork(pk));
            jdbc.update("UPDATE public.incident_investigation_work SET available_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE incident_pk=?",pk);
            InvestigationWorkClaim claim=claims.claim(1).getFirst();
            AIInvestigationResponse response=new AIInvestigationResponse(id,"classification","HIGH",.8,"T1110",
                    List.of("evidence"),"summary","review","MEDIUM",false);
            assertTrue(finalizer.persistAndComplete(claim,response));
            assertEquals("COMPLETED",works.findById(pk).orElseThrow().getStatus());
            assertEquals("INVESTIGATION_PERSISTED",works.findById(pk).orElseThrow().getResultCode());
            assertTrue(finalizer.completeAlreadyPersisted(claim)==false,"stale token cannot rewrite completed work");
            assertEquals(1,results.findByIncident_IncidentId(id).stream().count());
        } finally {
            if(pk!=null) {
                Long cleanupPk=pk;
                new TransactionTemplate(tx).executeWithoutResult(s->{
                jdbc.update("DELETE FROM public.incident_investigation_work WHERE incident_pk=?",cleanupPk);
                results.findByIncident_IncidentId(id).ifPresent(results::delete);
                incidents.deleteById(cleanupPk);
            }); }
        }
    }

    @Test void competingClaimsDoNotClaimTheSameWorkTwice() throws Exception {
        assertNoEligibleWorkOutsideThisTest();
        String id="INV-RACE-"+UUID.randomUUID(); Long pk=null;
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Incident incident=incidents.saveAndFlush(new Incident(id,"test","HIGH","OPEN","host","test", LocalDateTime.now()));
            pk=incident.getId();
            works.saveAndFlush(new IncidentInvestigationWork(pk));
            jdbc.update("UPDATE public.incident_investigation_work SET available_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE incident_pk=?",pk);
            CountDownLatch ready=new CountDownLatch(2), start=new CountDownLatch(1);
            Future<List<InvestigationWorkClaim>> a=pool.submit(()->claimTogether(ready,start));
            Future<List<InvestigationWorkClaim>> b=pool.submit(()->claimTogether(ready,start));
            assertTrue(ready.await(10,TimeUnit.SECONDS)); start.countDown();
            List<InvestigationWorkClaim> first=a.get(20,TimeUnit.SECONDS), second=b.get(20,TimeUnit.SECONDS);
            assertEquals(1,first.size()+second.size());
            assertEquals("IN_PROGRESS",works.findById(pk).orElseThrow().getStatus());
        } finally {
            pool.shutdownNow(); cleanup(pk,id);
        }
    }

    @Test void expiredLeaseRejectsOldTokenAndCanBeReclaimedWithFreshToken() {
        assertNoEligibleWorkOutsideThisTest();
        String id="INV-LEASE-"+UUID.randomUUID(); Long pk=null;
        try {
            Incident incident=incidents.saveAndFlush(new Incident(id,"test","HIGH","OPEN","host","test", LocalDateTime.now()));
            pk=incident.getId(); works.saveAndFlush(new IncidentInvestigationWork(pk));
            jdbc.update("UPDATE public.incident_investigation_work SET available_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE incident_pk=?",pk);
            InvestigationWorkClaim old=claims.claim(1).getFirst();
            jdbc.update("UPDATE public.incident_investigation_work SET lease_expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE incident_pk=?",pk);
            failures.fail(old,new IllegalStateException("late worker"));
            InvestigationWorkClaim fresh=claims.claim(1).getFirst();
            assertNotEquals(old.token(),fresh.token());
            assertEquals(2,fresh.attempt());
            assertFalse(finalizer.completeAlreadyPersisted(old));
        } finally { cleanup(pk,id); }
    }

    @Test void retryableFailuresExhaustOnFifthAttemptAndPermanentFailureStopsImmediately() {
        assertNoEligibleWorkOutsideThisTest();
        String retryId="INV-RETRY-"+UUID.randomUUID(), permanentId="INV-PERM-"+UUID.randomUUID();
        Long retryPk=null, permanentPk=null;
        try {
            Incident retryIncident=incidents.saveAndFlush(new Incident(retryId,"test","HIGH","OPEN","host","test", LocalDateTime.now()));
            retryPk=retryIncident.getId(); works.saveAndFlush(new IncidentInvestigationWork(retryPk));
            jdbc.update("UPDATE public.incident_investigation_work SET available_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE incident_pk=?",retryPk);
            for(short attempt=1;attempt<=5;attempt++) {
                InvestigationWorkClaim claim=claims.claim(1).getFirst();
                assertEquals(attempt,claim.attempt());
                failures.fail(claim,new IllegalStateException("provider unavailable"));
                IncidentInvestigationWork state=works.findById(retryPk).orElseThrow();
                if(attempt<5) {
                    assertEquals("PENDING",state.getStatus());
                    jdbc.update("UPDATE public.incident_investigation_work SET available_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE incident_pk=?",retryPk);
                } else {
                    assertEquals("FAILED",state.getStatus());
                    assertEquals(5,state.getAttemptCount());
                }
            }

            Incident permanentIncident=incidents.saveAndFlush(new Incident(permanentId,"test","HIGH","OPEN","host","test", LocalDateTime.now()));
            permanentPk=permanentIncident.getId(); works.saveAndFlush(new IncidentInvestigationWork(permanentPk));
            jdbc.update("UPDATE public.incident_investigation_work SET available_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE incident_pk=?",permanentPk);
            InvestigationWorkClaim claim=claims.claim(1).getFirst();
            failures.fail(claim,new IllegalArgumentException("invalid provider input"));
            assertEquals("FAILED",works.findById(permanentPk).orElseThrow().getStatus());
            assertEquals(1,works.findById(permanentPk).orElseThrow().getAttemptCount());
        } finally { cleanup(retryPk,retryId); cleanup(permanentPk,permanentId); }
    }

    @Test void persistenceFailureRollsBackInvestigationAndWorkCompletionTogether() {
        assertNoEligibleWorkOutsideThisTest();
        String id="INV-ROLLBACK-"+UUID.randomUUID(); Long pk=null;
        try {
            Incident incident=incidents.saveAndFlush(new Incident(id,"test","HIGH","OPEN","host","test", LocalDateTime.now()));
            pk=incident.getId(); works.saveAndFlush(new IncidentInvestigationWork(pk));
            jdbc.update("UPDATE public.incident_investigation_work SET available_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE incident_pk=?",pk);
            InvestigationWorkClaim claim=claims.claim(1).getFirst();
            AIInvestigationResponse invalid=new AIInvestigationResponse(id,null,"HIGH",.8,"T1110",
                    List.of("evidence"),"summary","review","MEDIUM",false);
            assertThrows(RuntimeException.class,()->finalizer.persistAndComplete(claim,invalid));
            assertTrue(results.findByIncident_IncidentId(id).isEmpty());
            assertEquals("IN_PROGRESS",works.findById(pk).orElseThrow().getStatus());
        } finally { cleanup(pk,id); }
    }

    private List<InvestigationWorkClaim> claimTogether(CountDownLatch ready,CountDownLatch start) throws Exception {
        ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("claim start timed out");
        return claims.claim(1);
    }

    private void assertNoEligibleWorkOutsideThisTest() {
        Integer count=jdbc.queryForObject("""
                SELECT count(*) FROM public.incident_investigation_work
                 WHERE (status='PENDING' AND available_at<=CURRENT_TIMESTAMP)
                    OR (status='IN_PROGRESS' AND lease_expires_at<=CURRENT_TIMESTAMP)
                """,Integer.class);
        assertEquals(0,count,"Refusing to claim pre-existing eligible work in shared disposable DB");
    }

    private void cleanup(Long pk,String incidentId) {
        if(pk==null) return;
        new TransactionTemplate(tx).executeWithoutResult(s->{
            jdbc.update("DELETE FROM public.incident_investigation_work WHERE incident_pk=?",pk);
            results.findByIncident_IncidentId(incidentId).ifPresent(results::delete);
            incidents.deleteById(pk);
        });
    }

    @SpringBootConfiguration @EnableAutoConfiguration @EntityScan("ai_soc_backend.incident.entity")
    @EnableJpaRepositories("ai_soc_backend.incident.repository")
    @Import({InvestigationWorkClaimService.class,InvestigationWorkFailureService.class,InvestigationWorkFinalizer.class})
    static class TestApplication { }
}
