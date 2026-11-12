package pl.rynek.engagement.api;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

/** Pytania i odpowiedzi na prawdziwym PostgreSQL; catalog i identity to atrapy. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class QuestionFlowTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    CatalogClient catalog;

    @MockitoBean
    IdentityClient identity;

    @BeforeEach
    void stubs() {
        given(jwtDecoder.decode(anyString())).willAnswer(call -> {
            String token = call.getArgument(0);
            if (!token.startsWith("user-")) {
                throw new BadJwtException("Nieważny token");
            }
            return Jwt.withTokenValue(token).header("alg", "RS256").subject(token.substring(5))
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build();
        });
        given(identity.displayName(any())).willReturn(Optional.of("Jan Kowalski"));
    }

    @Test
    void askEditAnswerAndDelete() throws Exception {
        UUID seller = UUID.randomUUID();
        UUID asker = UUID.randomUUID();
        UUID offer = offer(seller, true);

        mvc.perform(post("/api/v1/offers/" + offer + "/questions").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Czy działa?\"}"))
                .andExpect(status().isUnauthorized());
        ask(offer, asker, "  ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Napisz pytanie."));
        ask(offer, asker, "x".repeat(501)).andExpect(status().isBadRequest());
        ask(offer, seller, "Pytanie do siebie").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Nie możesz zadać pytania o własną ofertę."));
        ask(offer(seller, false), asker, "Czy działa?").andExpect(status().isNotFound());
        ask(UUID.randomUUID(), asker, "Czy działa?").andExpect(status().isNotFound());

        String body = ask(offer, asker, " Czy to ma gwarancję? ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("Czy to ma gwarancję?"))
                .andExpect(jsonPath("$.author.name").value("Jan Kowalski"))
                .andExpect(jsonPath("$.author.initials").value("JK"))
                .andExpect(jsonPath("$.likes").value(0))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.answer").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        // Lista jest publiczna; „mine” widzi tylko autor.
        mvc.perform(get("/api/v1/offers/" + offer + "/questions")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.items[0].mine").value(false));
        mvc.perform(get("/api/v1/offers/" + offer + "/questions").header(HttpHeaders.AUTHORIZATION, bearer(asker)))
                .andExpect(jsonPath("$.items[0].mine").value(true));
        // Nieważny token na publicznej liście to gość, nie 401.
        mvc.perform(get("/api/v1/offers/" + offer + "/questions").header(HttpHeaders.AUTHORIZATION, "Bearer zly"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].mine").value(false));

        edit("/api/v1/questions/" + id, UUID.randomUUID(), "Cudze").andExpect(status().isNotFound());
        edit("/api/v1/questions/" + id, asker, "Czy jest gwarancja 24 miesiące?").andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Czy jest gwarancja 24 miesiące?"));

        answer(id, asker, "Sam sobie").andExpect(status().isNotFound());
        answer(id, seller, "  ").andExpect(status().isBadRequest());
        answer(id, seller, "Tak, 24 miesiące.").andExpect(status().isCreated())
                .andExpect(jsonPath("$.answer.text").value("Tak, 24 miesiące."));
        answer(id, seller, "Jeszcze raz").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Na to pytanie już odpowiedziałeś."));
        edit("/api/v1/questions/" + id, asker, "Zmiana po odpowiedzi").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Sprzedawca już odpowiedział, więc nie możesz zmienić pytania."));
        mvc.perform(get("/api/v1/offers/" + offer + "/questions")).andExpect(jsonPath("$.items[0].answer.text").value("Tak, 24 miesiące."));

        mvc.perform(delete("/api/v1/questions/" + id).header(HttpHeaders.AUTHORIZATION, bearer(seller))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/questions/" + id).header(HttpHeaders.AUTHORIZATION, bearer(asker))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/offers/" + offer + "/questions")).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void usefulLikesSortingPagingAndCounts() throws Exception {
        UUID seller = UUID.randomUUID();
        UUID offer = offer(seller, true);
        UUID asker = UUID.randomUUID();
        String first = JsonPath.read(ask(offer, asker, "Pierwsze").andReturn().getResponse().getContentAsString(), "$.id");
        String second = JsonPath.read(ask(offer, asker, "Drugie").andReturn().getResponse().getContentAsString(), "$.id");
        ask(offer, asker, "Trzecie").andExpect(status().isCreated());

        like(first, UUID.randomUUID()).andExpect(status().isNoContent());
        UUID fan = UUID.randomUUID();
        like(first, fan).andExpect(status().isNoContent());
        like(first, fan).andExpect(status().isNoContent());
        like(second, fan).andExpect(status().isNoContent());
        like(UUID.randomUUID().toString(), fan).andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/offers/" + offer + "/questions?sort=newest")).andExpect(jsonPath("$.items[0].text").value("Trzecie"));
        mvc.perform(get("/api/v1/offers/" + offer + "/questions?sort=useful"))
                .andExpect(jsonPath("$.items[0].text").value("Pierwsze")).andExpect(jsonPath("$.items[0].likes").value(2))
                .andExpect(jsonPath("$.items[1].text").value("Drugie")).andExpect(jsonPath("$.items[1].likes").value(1))
                .andExpect(jsonPath("$.items[2].likes").value(0));
        mvc.perform(get("/api/v1/offers/" + offer + "/questions?sort=zly")).andExpect(status().isBadRequest());

        String next = JsonPath.read(mvc.perform(get("/api/v1/offers/" + offer + "/questions?sort=useful&limit=2")).andExpect(jsonPath("$.items", hasSize(2)))
                .andReturn().getResponse().getContentAsString(), "$.nextCursor");
        mvc.perform(get("/api/v1/offers/" + offer + "/questions?sort=useful&limit=2&cursor=" + next))
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.nextCursor").doesNotExist());

        UUID empty = UUID.randomUUID();
        mvc.perform(get("/internal/questions/counts").param("offerIds", offer + "," + empty))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.offerId == '" + offer + "')].count").value(3))
                .andExpect(jsonPath("$[?(@.offerId == '" + empty + "')].count").value(0));
    }

    private UUID offer(UUID owner, boolean available) {
        UUID id = UUID.randomUUID();
        given(catalog.offer(id)).willReturn(Optional.of(new OfferInfo(id, available, "Oferta", UUID.randomUUID(), owner)));
        return id;
    }

    private static String bearer(UUID user) {
        return "Bearer user-" + user;
    }

    private ResultActions ask(UUID offer, UUID user, String text) throws Exception {
        return mvc.perform(post("/api/v1/offers/" + offer + "/questions").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + text + "\"}"));
    }

    private ResultActions edit(String path, UUID user, String text) throws Exception {
        return mvc.perform(patch(path).header(HttpHeaders.AUTHORIZATION, bearer(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"" + text + "\"}"));
    }

    private ResultActions answer(String id, UUID user, String text) throws Exception {
        return mvc.perform(post("/api/v1/questions/" + id + "/answers").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + text + "\"}"));
    }

    private ResultActions like(String id, UUID user) throws Exception {
        return mvc.perform(put("/api/v1/questions/" + id + "/like").header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }
}
