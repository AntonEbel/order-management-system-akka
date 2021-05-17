package com.example.order.http;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorRef;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRoute;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import com.example.order.domain.OrderActor;
import com.example.order.fulfillment.FulfillmentActor;
import com.example.order.persistence.PersistenceActor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.ConfigFactory;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrderRoutesShould extends JUnitRouteTest {

    public static final TestKitJunitResource testKit = new TestKitJunitResource(ConfigFactory.parseString(
            "akka.actor.serialization-bindings { \"com.example.order.serialization.JsonSerializable\" = jackson-json}")
            .withFallback(EventSourcedBehaviorTestKit.config()));

    private static final ObjectMapper mapper = new ObjectMapper();

    private static ActorRef<PersistenceActor.PersistenceCommand> persistenceActor;
    private static ActorRef<OrderActor.Command> orderActor;

    TestRoute appRoute;

    @BeforeClass
    public static void beforeClass() {
        persistenceActor = testKit.spawn(PersistenceActor.create());
        ActorRef<FulfillmentActor.Command> fulfillmentActor = testKit.spawn(FulfillmentActor.create());
        orderActor = testKit.spawn(OrderActor.create(persistenceActor, fulfillmentActor));
    }

    @Before
    public void before() {
        appRoute = testRoute(new OrderRoutes(testKit.system(), persistenceActor, orderActor).userRoutes());
    }

    @Test
    public void create_and_change_payment_state() throws JsonProcessingException {

        String createdOrder = appRoute.run(HttpRequest.POST("/orders")
                .withEntity(MediaTypes.APPLICATION_JSON.toContentType(), createOrderJson()))
                .assertStatusCode(StatusCodes.CREATED)
                .assertMediaType("application/json")
                .entityString();

        String id = mapper.readTree(createdOrder).get("id").asText();

        appRoute.run(HttpRequest.PATCH("/orders/" + id)
                .withEntity(MediaTypes.APPLICATION_JSON.toContentType(), createOrderStateJson("PAID")))
                .assertStatusCode(StatusCodes.OK)
                .assertMediaType("application/json");

        JsonNode closedOrder = mapper.readTree(appRoute.run(HttpRequest.GET("/orders/" + id))
                .assertStatusCode(StatusCodes.OK)
                .assertMediaType("application/json")
                .entityString());

        assertThat(closedOrder.get("state").asText()).isEqualTo("CLOSED");
        assertThat(closedOrder.has("fulfillmentResult")).isTrue();
        assertThat(closedOrder.has("items")).isTrue();
        assertThat(closedOrder.has("id")).isTrue();
    }

    @Test
    public void create_and_try_to_change_invalid_payment_state() throws JsonProcessingException {

        String createdOrder = appRoute.run(HttpRequest.POST("/orders")
                .withEntity(MediaTypes.APPLICATION_JSON.toContentType(), createOrderJson()))
                .assertStatusCode(StatusCodes.CREATED)
                .assertMediaType("application/json")
                .entityString();

        String id = mapper.readTree(createdOrder).get("id").asText();

        appRoute.run(HttpRequest.PATCH("/orders/" + id)
                .withEntity(MediaTypes.APPLICATION_JSON.toContentType(), createOrderStateJson("CLOSED")))
                .assertStatusCode(StatusCodes.BAD_REQUEST)
                .assertMediaType("application/json");
    }

    private String createOrderJson() {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode item = mapper.createObjectNode();
        item.put("TV", 1);
        root.set("items", item);
        return root.toString();
    }

    private String createOrderStateJson(String state) {
        ObjectNode root = mapper.createObjectNode();
        root.put("state", state);
        return root.toString();
    }

}