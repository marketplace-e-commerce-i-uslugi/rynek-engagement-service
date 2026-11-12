package pl.rynek.engagement.api;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import pl.rynek.engagement.clients.BookingClient;
import pl.rynek.engagement.clients.IdentityClient;
import pl.rynek.engagement.clients.OrderClient;
import pl.rynek.engagement.reviews.ReviewService;

/** Opinie na prawdziwym PostgreSQL; order-service i booking-service to atrapy. */
@SpringBootTest(properties = "rynek.reviews.notify-retry=1h")
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ReviewFlowTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ReviewService service;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    OrderClient orders;

    @MockitoBean
    BookingClient bookings;

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
        given(identity.displayName(any())).willReturn(Optional.of("Anna Nowak"));
        given(orders.reviewed(any())).willReturn(true);
        given(bookings.reviewed(any())).willReturn(true);
    }

    @Test
    void orderReviewNeedsBuyerAndCompletedOrderAndIsOnlyOnce() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID channel = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        given(orders.order(order)).willReturn(Optional.of(new OrderClient.Order(order, buyer, seller, channel, "SHIPPED", "Wiertarka")));

        review("/api/v1/orders/" + order + "/review", buyer, 5, "Super").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Opinię możesz wystawić po zrealizowaniu zamówienia."));
        given(orders.order(order)).willReturn(Optional.of(new OrderClient.Order(order, buyer, seller, channel, "COMPLETED", "Wiertarka")));
        review("/api/v1/orders/" + order + "/review", UUID.randomUUID(), 5, "Super").andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/orders/" + order + "/review").contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"text\":\"Super\"}"))
                .andExpect(status().isUnauthorized());
        review("/api/v1/orders/" + order + "/review", buyer, 6, "Super").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Wybierz ocenę od 1 do 5."));
        review("/api/v1/orders/" + order + "/review", buyer, 5, "  ").andExpect(status().isBadRequest());

        review("/api/v1/orders/" + order + "/review", buyer, 5, " Szybko i bez problemów ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.text").value("Szybko i bez problemów"))
                .andExpect(jsonPath("$.verifiedPurchase").value(true))
                .andExpect(jsonPath("$.author.name").value("Anna Nowak"))
                .andExpect(jsonPath("$.author.initials").value("AN"))
                .andExpect(jsonPath("$.offerTitle").value("Wiertarka"));
        verify(orders).reviewed(order);
        review("/api/v1/orders/" + order + "/review", buyer, 4, "Jeszcze raz").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("To zamówienie ma już Twoją opinię."));
    }

    @Test
    void bookingReviewAndSummaryAndSellerReply() throws Exception {
        UUID customer = UUID.randomUUID();
        UUID provider = UUID.randomUUID();
        UUID channel = UUID.randomUUID();
        UUID booking = UUID.randomUUID();
        given(bookings.booking(booking)).willReturn(Optional.of(
                new BookingClient.Booking(booking, UUID.randomUUID(), "Malowanie", channel, customer, provider, "CONFIRMED")));
        review("/api/v1/bookings/" + booking + "/review", customer, 4, "Dobrze").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Opinię możesz wystawić po wykonaniu usługi."));
        given(bookings.booking(booking)).willReturn(Optional.of(
                new BookingClient.Booking(booking, UUID.randomUUID(), "Malowanie", channel, customer, provider, "COMPLETED")));
        String created = review("/api/v1/bookings/" + booking + "/review", customer, 4, "Dobrze").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reviewId = JsonPath.read(created, "$.id");
        verify(bookings).reviewed(booking);

        // Druga opinia o tym samym kanale, z zamówienia.
        UUID order = UUID.randomUUID();
        given(orders.order(order)).willReturn(Optional.of(new OrderClient.Order(order, customer, provider, channel, "COMPLETED", "Farba")));
        review("/api/v1/orders/" + order + "/review", customer, 2, "Słabo").andExpect(status().isCreated());

        mvc.perform(get("/api/v1/channels/" + channel + "/rating-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.average").value(3.0))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.distribution.5").value(0))
                .andExpect(jsonPath("$.distribution.4").value(1))
                .andExpect(jsonPath("$.distribution.2").value(1));
        mvc.perform(get("/api/v1/channels/" + UUID.randomUUID() + "/rating-summary"))
                .andExpect(jsonPath("$.count").value(0)).andExpect(jsonPath("$.average").value(0.0));
        mvc.perform(get("/api/v1/channels/" + channel + "/reviews?limit=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].text").value("Słabo"))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty());
        String next = JsonPath.read(mvc.perform(get("/api/v1/channels/" + channel + "/reviews?limit=1")).andReturn().getResponse().getContentAsString(),
                "$.nextCursor");
        mvc.perform(get("/api/v1/channels/" + channel + "/reviews?limit=1&cursor=" + next))
                .andExpect(jsonPath("$.items[0].text").value("Dobrze"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        mvc.perform(get("/api/v1/channels/" + channel + "/reviews?cursor=zly")).andExpect(status().isBadRequest());

        reply(reviewId, UUID.randomUUID(), "Dziękuję").andExpect(status().isNotFound());
        reply(reviewId, provider, "Dziękuję za opinię").andExpect(status().isOk())
                .andExpect(jsonPath("$.reply.text").value("Dziękuję za opinię"));
        reply(reviewId, provider, "Jeszcze raz").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Na tę opinię już odpowiedziałeś."));
        mvc.perform(get("/api/v1/channels/" + channel + "/reviews"))
                .andExpect(jsonPath("$.items[1].reply.text").value("Dziękuję za opinię"));

        mvc.perform(get("/internal/ratings").param("channelIds", channel + "," + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].channelId").value(channel.toString()))
                .andExpect(jsonPath("$[0].average").value(3.0))
                .andExpect(jsonPath("$[0].count").value(2))
                .andExpect(jsonPath("$[1].count").value(0));
    }

    @Test
    void sourceIsNotifiedAgainWhenItWasDown() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        given(orders.order(order)).willReturn(Optional.of(new OrderClient.Order(order, buyer, UUID.randomUUID(), UUID.randomUUID(), "COMPLETED", "Kabel")));
        given(orders.reviewed(order)).willReturn(false);
        review("/api/v1/orders/" + order + "/review", buyer, 5, "Działa").andExpect(status().isCreated());
        verify(orders).reviewed(order);

        given(orders.reviewed(order)).willReturn(true);
        service.notifySources();
        verify(orders, org.mockito.Mockito.times(2)).reviewed(order);
        service.notifySources();
        verify(orders, org.mockito.Mockito.times(2)).reviewed(order);
        verify(bookings, never()).reviewed(any());
    }

    private ResultActions review(String path, UUID user, int rating, String text) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer user-" + user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":" + rating + ",\"text\":\"" + text + "\"}"));
    }

    private ResultActions reply(String reviewId, UUID user, String text) throws Exception {
        return mvc.perform(post("/api/v1/reviews/" + reviewId + "/reply").header(HttpHeaders.AUTHORIZATION, "Bearer user-" + user)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + text + "\"}"));
    }
}
