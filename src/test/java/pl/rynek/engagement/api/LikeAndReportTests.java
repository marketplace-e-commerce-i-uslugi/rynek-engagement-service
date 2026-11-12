package pl.rynek.engagement.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import pl.rynek.engagement.clients.OrderClient;

/** Polubienia ofert i zgłoszenia treści na prawdziwym PostgreSQL; catalog, identity i order to atrapy. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class LikeAndReportTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    CatalogClient catalog;

    @MockitoBean
    IdentityClient identity;

    @MockitoBean
    OrderClient orders;

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
        given(orders.reviewed(any())).willReturn(true);
    }

    @Test
    void offerLikeIsIdempotentAndUndoable() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID fan = UUID.randomUUID();
        UUID offer = offer(owner, true);

        mvc.perform(put("/api/v1/offers/" + offer + "/like")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/offers/" + offer + "/like").header(HttpHeaders.AUTHORIZATION, bearer(fan))).andExpect(status().isNoContent());
        mvc.perform(put("/api/v1/offers/" + offer + "/like").header(HttpHeaders.AUTHORIZATION, bearer(fan))).andExpect(status().isNoContent());
        liked(offer, fan, true);
        liked(offer, UUID.randomUUID(), false);

        mvc.perform(delete("/api/v1/offers/" + offer + "/like").header(HttpHeaders.AUTHORIZATION, bearer(fan))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/offers/" + offer + "/like").header(HttpHeaders.AUTHORIZATION, bearer(fan))).andExpect(status().isNoContent());
        liked(offer, fan, false);

        mvc.perform(put("/api/v1/offers/" + offer(owner, false) + "/like").header(HttpHeaders.AUTHORIZATION, bearer(fan))).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/offers/" + UUID.randomUUID() + "/like").header(HttpHeaders.AUTHORIZATION, bearer(fan))).andExpect(status().isNotFound());
    }

    @Test
    void reportOfferQuestionReviewAndChat() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID reporter = UUID.randomUUID();
        UUID offer = offer(owner, true);

        mvc.perform(post("/api/v1/reports").contentType(MediaType.APPLICATION_JSON).content(body("OFFER", offer, "Spam")))
                .andExpect(status().isUnauthorized());
        report("OFFER", offer, reporter, " ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Wybierz powód zgłoszenia."));
        report("INNE", offer, reporter, "Spam").andExpect(status().isBadRequest());
        report("OFFER", offer, owner, "Spam").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Nie możesz zgłosić własnej treści."));
        report("OFFER", offer(owner, false), reporter, "Spam").andExpect(status().isNotFound());
        report("OFFER", offer, reporter, "Oszustwo lub podróbka").andExpect(status().isCreated());
        report("OFFER", offer, reporter, "Oszustwo lub podróbka").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Już zgłosiłeś tę treść. Sprawdzimy ją."));
        report("OFFER", offer, UUID.randomUUID(), "Spam").andExpect(status().isCreated());

        String q = JsonPath.read(mvc.perform(post("/api/v1/offers/" + offer + "/questions").header(HttpHeaders.AUTHORIZATION, bearer(reporter))
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Pytanie\"}")).andReturn().getResponse().getContentAsString(), "$.id");
        UUID question = UUID.fromString(q);
        report("QUESTION", question, reporter, "Spam").andExpect(status().isConflict());
        report("QUESTION", question, owner, "Obraźliwe").andExpect(status().isCreated());
        report("QUESTION", UUID.randomUUID(), owner, "Obraźliwe").andExpect(status().isNotFound());

        UUID buyer = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        given(orders.order(order)).willReturn(Optional.of(new OrderClient.Order(order, buyer, owner, UUID.randomUUID(), "COMPLETED", "Kabel")));
        String r = JsonPath.read(mvc.perform(post("/api/v1/orders/" + order + "/review").header(HttpHeaders.AUTHORIZATION, bearer(buyer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":1,\"text\":\"Okropne\"}")).andReturn().getResponse().getContentAsString(), "$.id");
        UUID review = UUID.fromString(r);
        report("REVIEW", review, buyer, "Spam").andExpect(status().isConflict());
        report("REVIEW", review, owner, "Nieprawdziwa opinia").andExpect(status().isCreated());

        report("CHAT", UUID.randomUUID(), reporter, "Wyzwiska").andExpect(status().isCreated());
    }

    private UUID offer(UUID owner, boolean available) {
        UUID id = UUID.randomUUID();
        given(catalog.offer(id)).willReturn(Optional.of(new OfferInfo(id, available, "Oferta", UUID.randomUUID(), owner)));
        return id;
    }

    private static String bearer(UUID user) {
        return "Bearer user-" + user;
    }

    private void liked(UUID offer, UUID user, boolean expected) throws Exception {
        mvc.perform(get("/internal/offers/" + offer + "/liked").param("userId", user.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.liked").value(expected));
    }

    private static String body(String type, UUID target, String reason) {
        return "{\"targetType\":\"" + type + "\",\"targetId\":\"" + target + "\",\"reason\":\"" + reason + "\"}";
    }

    private ResultActions report(String type, UUID target, UUID user, String reason) throws Exception {
        return mvc.perform(post("/api/v1/reports").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(body(type, target, reason)));
    }
}
