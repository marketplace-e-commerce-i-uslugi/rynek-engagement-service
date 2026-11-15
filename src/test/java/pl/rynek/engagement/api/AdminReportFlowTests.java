package pl.rynek.engagement.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.rynek.engagement.TestcontainersConfig;
import pl.rynek.engagement.clients.CatalogClient;
import pl.rynek.engagement.clients.CatalogClient.OfferInfo;
import pl.rynek.engagement.clients.IdentityClient;
import pl.rynek.engagement.clients.NotificationClient;
import pl.rynek.engagement.clients.OrderClient;

/** Moderacja zgłoszeń na prawdziwym PostgreSQL; catalog, identity, order i notification to atrapy. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AdminReportFlowTests {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean NotificationClient notifications;
    @MockitoBean CatalogClient catalog;
    @MockitoBean IdentityClient identity;
    @MockitoBean OrderClient orders;

    @BeforeEach
    void setUp() {
        given(jwtDecoder.decode(anyString())).willAnswer(call -> {
            String token = call.getArgument(0);
            boolean admin = token.startsWith("admin-");
            if (!admin && !token.startsWith("user-")) {
                throw new BadJwtException("Nieważny token");
            }
            return Jwt.withTokenValue(token).header("alg", "RS256").subject(token.substring(token.indexOf('-') + 1))
                    .claim("roles", admin ? List.of("ADMIN") : List.of("BUYER")).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build();
        });
        given(identity.displayName(any())).willReturn(Optional.of("Jan Kowalski"));
        given(orders.reviewed(any())).willReturn(true);
        given(catalog.setBlocked(any(), eq(true))).willReturn(true);
        given(catalog.setBlocked(any(), eq(false))).willReturn(true);
    }

    @Test
    void panelWymagaRoliAdmin() throws Exception {
        mvc.perform(get("/api/v1/admin/reports")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/reports").header(HttpHeaders.AUTHORIZATION, user(UUID.randomUUID()))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", is("Ta operacja wymaga uprawnień administratora.")));
        mvc.perform(post("/api/v1/admin/reports/" + UUID.randomUUID() + "/reject").header(HttpHeaders.AUTHORIZATION, user(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/offers/" + UUID.randomUUID() + "/unblock").header(HttpHeaders.AUTHORIZATION, user(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void kolejkaPokazujeTrescIZgloszeniaZFiltrami() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID offer = offer(owner, "Wiertarka udarowa");
        UUID asker = UUID.randomUUID();
        UUID question = question(offer, asker, "Czy to kradzione?");
        report("OFFER", offer, UUID.randomUUID(), "Oszustwo");
        report("OFFER", offer, UUID.randomUUID(), "Podróbka");
        report("QUESTION", question, UUID.randomUUID(), "Obraźliwe");
        report("CHAT", UUID.randomUUID(), UUID.randomUUID(), "Groźby");

        String admin = admin();
        // Otwarte od najstarszego; oferta zgłoszona dwa razy ma licznik 2, podgląd z catalog i pytania.
        ResultActions all = mvc.perform(get("/api/v1/admin/reports").param("status", "OPEN").header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isOk());
        String body = all.andReturn().getResponse().getContentAsString();
        List<Integer> counts = JsonPath.read(body, "$.items[?(@.targetId=='" + offer + "')].reportsCount");
        org.assertj.core.api.Assertions.assertThat(counts).containsExactly(2, 2);
        List<String> titles = JsonPath.read(body, "$.items[?(@.targetId=='" + offer + "')].preview.title");
        org.assertj.core.api.Assertions.assertThat(titles).containsOnly("Wiertarka udarowa");
        mvc.perform(get("/api/v1/admin/reports").param("type", "question").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.items[0].preview.text", is("Czy to kradzione?")))
                .andExpect(jsonPath("$.items[0].preview.author", is("Jan Kowalski"))).andExpect(jsonPath("$.items[0].preview.link", is("/oferta/" + offer)))
                .andExpect(jsonPath("$.items[0].status", is("OPEN")));
        mvc.perform(get("/api/v1/admin/reports").param("type", "CHAT").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.items[0].preview.title", is("Rozmowa")));
        mvc.perform(get("/api/v1/admin/reports").param("status", "ZLY").header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/reports").param("type", "ZLY").header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/reports").param("status", "OPEN").param("limit", "1").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.nextCursor", notNullValue()));
    }

    @Test
    void odrzucenieZamykaWszystkieZgloszeniaOTrescIPowiadamiaZglaszajacych() throws Exception {
        UUID offer = offer(UUID.randomUUID(), "Słuchawki");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        report("OFFER", offer, first, "Spam");
        report("OFFER", offer, second, "Oszustwo");
        String admin = admin();
        String id = firstReportOn(offer, admin);

        mvc.perform(post("/api/v1/admin/reports/" + id + "/reject").header(HttpHeaders.AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Oferta w porządku\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("REJECTED"))).andExpect(jsonPath("$.note", is("Oferta w porządku")))
                .andExpect(jsonPath("$.contentRemoved", is(false))).andExpect(jsonPath("$.resolvedAt", notNullValue()));
        // Drugie zgłoszenie o tej samej ofercie zamknięte razem z pierwszym; obaj zgłaszający dostali powiadomienie.
        mvc.perform(get("/api/v1/admin/reports").param("status", "OPEN").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.items[?(@.targetId=='" + offer + "')]", hasSize(0)));
        mvc.perform(get("/api/v1/admin/reports").param("status", "REJECTED").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.items[?(@.targetId=='" + offer + "')]", hasSize(2)));
        verify(notifications).send(eq(first), eq("messages"), eq("Rozpatrzyliśmy Twoje zgłoszenie"), anyString(), any(), anyString());
        verify(notifications).send(eq(second), eq("messages"), eq("Rozpatrzyliśmy Twoje zgłoszenie"), anyString(), any(), anyString());
        verify(catalog, never()).setBlocked(eq(offer), eq(true));
        // Ponowne rozpatrzenie: 409.
        mvc.perform(post("/api/v1/admin/reports/" + id + "/reject").header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/admin/reports/" + UUID.randomUUID() + "/reject").header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/admin/reports/nie-uuid/reject").header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isNotFound());
    }

    @Test
    void usuniecieTresciUsuwaPytanieIPowiadamiaAutora() throws Exception {
        UUID offer = offer(UUID.randomUUID(), "Rower");
        UUID author = UUID.randomUUID();
        UUID question = question(offer, author, "Obraźliwe pytanie");
        report("QUESTION", question, UUID.randomUUID(), "Wulgaryzmy");
        String admin = admin();
        String id = firstReportOn(question, admin);

        mvc.perform(post("/api/v1/admin/reports/" + id + "/resolve").header(HttpHeaders.AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"removeContent\":true,\"note\":\"Wulgaryzmy\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("RESOLVED"))).andExpect(jsonPath("$.contentRemoved", is(true)))
                .andExpect(jsonPath("$.preview.available", is(false)));
        mvc.perform(get("/api/v1/offers/" + offer + "/questions")).andExpect(jsonPath("$.items", hasSize(0)));
        verify(notifications).send(eq(author), eq("messages"), eq("Usunęliśmy Twoje pytanie"), anyString(), eq("/oferta/" + offer), anyString());
    }

    @Test
    void usuniecieOpiniiIBlokadaOfertyPrzezCatalog() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID offer = offer(owner, "Laptop");
        UUID reviewer = UUID.randomUUID();
        UUID review = UUID.randomUUID();
        jdbc.update("insert into reviews (id, channel_id, seller_id, author_id, author_name, source_type, source_id, offer_title, rating, text, verified, source_notified, created_at) "
                + "values (?, ?, ?, ?, 'Anna', 'ORDER', ?, 'Laptop', 1, 'Oszust i złodziej', true, true, ?)", review, UUID.randomUUID(), owner, reviewer, UUID.randomUUID(),
                Timestamp.from(Instant.now()));
        report("REVIEW", review, UUID.randomUUID(), "Zniewaga");
        report("OFFER", offer, UUID.randomUUID(), "Fałszywa oferta");
        String admin = admin();

        mvc.perform(post("/api/v1/admin/reports/" + firstReportOn(review, admin) + "/resolve").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"removeContent\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contentRemoved", is(true)));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from reviews where id = ?", Long.class, review)).isZero();
        verify(notifications).send(eq(reviewer), eq("messages"), eq("Usunęliśmy Twoją opinię"), anyString(), any(), anyString());

        mvc.perform(post("/api/v1/admin/reports/" + firstReportOn(offer, admin) + "/resolve").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"removeContent\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("RESOLVED")));
        verify(catalog).setBlocked(offer, true);
        verify(notifications).send(eq(owner), eq("messages"), eq("Zablokowaliśmy Twoją ofertę"), anyString(), eq("/sprzedawca/oferty"), anyString());

        // Zdjęcie blokady: catalog i powiadomienie dla sprzedawcy.
        mvc.perform(post("/api/v1/admin/offers/" + offer + "/unblock").header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isNoContent());
        verify(catalog).setBlocked(offer, false);
        verify(notifications).send(eq(owner), eq("messages"), eq("Zdjęliśmy blokadę z Twojej oferty"), anyString(), eq("/sprzedawca/oferty"), anyString());
        mvc.perform(post("/api/v1/admin/offers/nie-uuid/unblock").header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isNotFound());
        UUID gone = UUID.randomUUID();
        given(catalog.offer(gone)).willReturn(Optional.empty());
        mvc.perform(post("/api/v1/admin/offers/" + gone + "/unblock").header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isNotFound());
    }

    @Test
    void rozmowyNieMoznaUsunacAleMoznaJeRozpatrzycBezUsuwania_aNiedostepnyCatalogNicNieZmienia() throws Exception {
        UUID chat = UUID.randomUUID();
        report("CHAT", chat, UUID.randomUUID(), "Groźby");
        String admin = admin();
        String id = firstReportOn(chat, admin);
        mvc.perform(post("/api/v1/admin/reports/" + id + "/resolve").header(HttpHeaders.AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"removeContent\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", is("Rozmów nie usuwamy z tego panelu. Rozpatrz zgłoszenie bez usuwania treści albo je odrzuć.")));
        mvc.perform(post("/api/v1/admin/reports/" + id + "/resolve").header(HttpHeaders.AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"removeContent\":false,\"note\":\"Załatwione z użytkownikami\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("RESOLVED"))).andExpect(jsonPath("$.contentRemoved", is(false)));

        UUID offer = offer(UUID.randomUUID(), "Telewizor");
        report("OFFER", offer, UUID.randomUUID(), "Oszustwo");
        String offerReport = firstReportOn(offer, admin);
        given(catalog.setBlocked(offer, true)).willThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Katalog ofert jest niedostępny. Spróbuj ponownie za chwilę."));
        mvc.perform(post("/api/v1/admin/reports/" + offerReport + "/resolve").header(HttpHeaders.AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"removeContent\":true}")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/admin/reports").param("status", "OPEN").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.items[?(@.targetId=='" + offer + "')]", hasSize(1)));
        mvc.perform(post("/api/v1/admin/reports/" + offerReport + "/resolve").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"" + "x".repeat(501) + "\"}")).andExpect(status().isBadRequest());
    }

    // --- pomocnicze ---

    private UUID offer(UUID owner, String title) {
        UUID id = UUID.randomUUID();
        given(catalog.offer(id)).willReturn(Optional.of(new OfferInfo(id, true, title, UUID.randomUUID(), owner)));
        return id;
    }

    private UUID question(UUID offer, UUID author, String text) throws Exception {
        String body = mvc.perform(post("/api/v1/offers/" + offer + "/questions").header(HttpHeaders.AUTHORIZATION, user(author))
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + text + "\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private ResultActions report(String type, UUID target, UUID reporter, String reason) throws Exception {
        return mvc.perform(post("/api/v1/reports").header(HttpHeaders.AUTHORIZATION, user(reporter)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"" + type + "\",\"targetId\":\"" + target + "\",\"reason\":\"" + reason + "\"}")).andExpect(status().isCreated());
    }

    /** Id pierwszego otwartego zgłoszenia o treści (najstarsze jest pierwsze). */
    private String firstReportOn(UUID target, String admin) throws Exception {
        String body = mvc.perform(get("/api/v1/admin/reports").param("status", "OPEN").param("limit", "50").header(HttpHeaders.AUTHORIZATION, admin))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.items[?(@.targetId=='" + target + "')].id");
        return ids.getFirst();
    }

    private static String user(UUID id) {
        return "Bearer user-" + id;
    }

    private static String admin() {
        return "Bearer admin-" + UUID.randomUUID();
    }
}
