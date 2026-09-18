package cz.demo.caselaw.justice;

import cz.demo.caselaw.domain.Decision;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The cold-cache path: fetch(id, day) must re-list the day instead of degrading to the court code. */
class JusticeOpenDataClientTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String BASE = "https://rozhodnuti.example";

    private static final String LIST_PAGE = """
            {"totalPages":1,"items":[{
               "odkaz":"/api/finaldoc/11111111-1111-1111-1111-111111111111",
               "jednaciCislo":"1 C 1/2023",
               "soud":"Okresní soud v Ostravě",
               "ecli":"ECLI:CZ:OSOS:2024:1.C.1.2023.1",
               "predmetRizeni":"náhrada škody",
               "datumVydani":"2023-11-30",
               "datumZverejneni":"2024-03-01",
               "klicovaSlova":["náhrada škody","smlouva"],
               "zminenaUstanoveni":["§ 2910 z. č. 89/2012 Sb."]}]}
            """;

    private static final String FINAL_DOC = """
            {"metadata":{"courtCode":"OSTR","ecli":"ECLI:CZ:OSOS:2024:1.C.1.2023.1"},
             "verdict":"I. Žaloba se zamítá.","justification":"1. Odůvodnění."}
            """;

    private record Fixture(JusticeOpenDataClient client, MockRestServiceServer server) {}

    private static Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        JusticeOpenDataClient client = new JusticeOpenDataClient(
                builder, new DecisionParser(), new JusticeProperties(BASE, 1000));
        return new Fixture(client, server);
    }

    private static void expectList(MockRestServiceServer server) {
        server.expect(requestTo(BASE + "/api/opendata/2024/3/1?page=0"))
                .andRespond(withSuccess(LIST_PAGE, MediaType.APPLICATION_JSON));
    }

    private static void expectFinalDoc(MockRestServiceServer server) {
        server.expect(requestTo(BASE + "/api/finaldoc/" + ID))
                .andRespond(withSuccess(FINAL_DOC, MediaType.APPLICATION_JSON));
    }

    @Test
    void coldCacheReListsTheDayAndKeepsTheListMetadata() {
        Fixture f = fixture();
        expectList(f.server());
        expectFinalDoc(f.server());

        assertThat(f.client().cachedListEntries()).isZero();
        Decision d = f.client().fetch(ID, LocalDate.of(2024, 3, 1));

        assertThat(d.court()).isEqualTo("Okresní soud v Ostravě");
        assertThat(d.keywords()).containsExactly("náhrada škody", "smlouva");
        assertThat(d.provisions()).containsExactly("§ 2910 z. č. 89/2012 Sb.");
        assertThat(d.caseNumber()).isEqualTo("1 C 1/2023");
        f.server().verify();
    }

    @Test
    void warmCacheDoesNotListAgain() {
        Fixture f = fixture();
        expectList(f.server());
        expectFinalDoc(f.server());

        f.client().listDecisionIds(LocalDate.of(2024, 3, 1));
        assertThat(f.client().cachedListEntries()).isOne();

        // Only the finaldoc call is left; a second list request would fail the strict expectations.
        Decision d = f.client().fetch(ID, LocalDate.of(2024, 3, 1));
        assertThat(d.court()).isEqualTo("Okresní soud v Ostravě");
        f.server().verify();
    }

    @Test
    void unknownDayFallsBackToTheFinalDocOnly() {
        Fixture f = fixture();
        expectFinalDoc(f.server());

        Decision d = f.client().fetch(ID, null);

        assertThat(d.court()).isEqualTo("OSTR");
        assertThat(d.keywords()).isEmpty();
        f.server().verify();
    }
}
