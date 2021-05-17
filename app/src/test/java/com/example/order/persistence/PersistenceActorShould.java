package com.example.order.persistence;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.pattern.StatusReply;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit.CommandResultWithReply;
import com.typesafe.config.ConfigFactory;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JUnitParamsRunner.class)
public class PersistenceActorShould {

    static final Order ORDER = new Order("1", Map.of("TV", 1), OrderState.CREATED, FulfillmentResult.NO_RESULT);

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource(ConfigFactory.parseString(
            "akka.actor.serialization-bindings { \"com.example.order.serialization.JsonSerializable\" = jackson-json}")
            .withFallback(EventSourcedBehaviorTestKit.config()));

    final EventSourcedBehaviorTestKit<PersistenceActor.PersistenceCommand, Events.Event, State> eventSourcedTestKit =
            EventSourcedBehaviorTestKit.create(testKit.system(), PersistenceActor.create());

    @Test
    public void createOrder() {
        Order reply = createOrder(ORDER).reply().getValue();

        assertThat(reply).isEqualTo(ORDER);
    }

    @Test
    public void getOrder() {
        createOrder(ORDER);

        assertThat(getOrder(ORDER.id).reply().getValue()).isEqualTo(ORDER);
    }

    @Test
    @Parameters({
            "CREATED, PAID",
            "PAID, IN_FULFILLMENT",
    })
    public void changeState(OrderState from, OrderState to) {
        createOrder(orderWithState(from));

        Order result = changeState(ORDER.id, to).reply().getValue();

        assertThat(result).isEqualTo(orderWithState(to));
    }

    @Test
    @Parameters({
            "CREATED, CREATED",
            "CREATED, IN_FULFILLMENT",
            "CREATED, CLOSED",
            "PAID, PAID",
            "PAID, CREATED",
            "PAID, CLOSED",
            "IN_FULFILLMENT, IN_FULFILLMENT",
            "IN_FULFILLMENT, CREATED",
            "IN_FULFILLMENT, PAID",
            "IN_FULFILLMENT, CLOSED",
            "CLOSED, CLOSED",
            "CLOSED, CREATED",
            "CLOSED, PAID",
            "CLOSED, IN_FULFILLMENT",
    })
    public void notChangeState(OrderState from, OrderState to) {
        createOrder(orderWithState(from));

        StatusReply<Order> reply = changeState(ORDER.id, to).reply();

        Order result = getOrder(ORDER.id).reply().getValue();
        assertThat(reply.isSuccess()).isFalse();
        assertThat(result.state).isEqualTo(from);
    }

    @Test
    @Parameters({"CREATED", "PAID", "CLOSED"})
    public void notCloseOrderOnInvalidState(OrderState from) {
        createOrder(orderWithState(from));

        StatusReply<Order> reply = closeOrder(ORDER.id, FulfillmentResult.SUCCESS).reply();

        Order result = getOrder(ORDER.id).reply().getValue();
        assertThat(reply.isSuccess()).isFalse();
        assertThat(result.state).isEqualTo(from);
    }

    @Test
    @Parameters({"SUCCESS", "FAILURE"})
    public void CloseOrderWith(FulfillmentResult result) {
        createOrder(orderWithState(OrderState.IN_FULFILLMENT));

        Order order = closeOrder(ORDER.id, result).reply().getValue();

        assertThat(order.fulfillmentResult).isEqualTo(result);
        assertThat(order.state).isEqualTo(OrderState.CLOSED);
    }

    private Order orderWithState(OrderState from) {
        return ORDER.toBuilder().state(from).build();
    }

    @Test
    public void dontGetMissingOrder() {
        assertThat(getOrder("no existing id").reply().isError()).isTrue();
    }


    private CommandResultWithReply<PersistenceActor.PersistenceCommand, Events.Event, State, StatusReply<Order>> createOrder(Order order) {
        return eventSourcedTestKit.runCommand(replyTo -> new PersistenceActor.Create(order, replyTo));
    }

    private CommandResultWithReply<PersistenceActor.PersistenceCommand, Events.Event, State, StatusReply<Order>> getOrder(String orderId) {
        return eventSourcedTestKit.runCommand(replyTo -> new PersistenceActor.Get(orderId, replyTo));
    }

    private CommandResultWithReply<PersistenceActor.PersistenceCommand, Events.Event, State, StatusReply<Order>> changeState(String orderId, OrderState state) {
        return eventSourcedTestKit.runCommand(replyTo -> new PersistenceActor.ChangeState(orderId, state, replyTo));
    }

    private CommandResultWithReply<PersistenceActor.PersistenceCommand, Events.Event, State, StatusReply<Order>> closeOrder(String orderId, FulfillmentResult result) {
        return eventSourcedTestKit.runCommand(replyTo -> new PersistenceActor.Close(orderId, result, replyTo));
    }
}