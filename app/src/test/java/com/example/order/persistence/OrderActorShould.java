package com.example.order.persistence;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.pattern.StatusReply;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import com.typesafe.config.ConfigFactory;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Map;
import java.util.UUID;

import static com.example.order.persistence.Commands.*;
import static com.example.order.persistence.Commands.Command;
import static com.example.order.persistence.Commands.Create;
import static org.assertj.core.api.Assertions.assertThat;

public class OrderActorShould {


    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource(ConfigFactory.parseString(
            "akka.actor.serialization-bindings { \"com.example.order.serialization.JsonSerializable\" = jackson-json}")
            .withFallback(EventSourcedBehaviorTestKit.config()));

    private final EventSourcedBehaviorTestKit<Command, Events.Event, State> eventSourcedTestKit =
            EventSourcedBehaviorTestKit.create(testKit.system(), OrderActor.create());

    @Test
    public void createOrder() {
        Order order = new Order("1", Map.of("milk", 1), OrderState.CREATED);

        Order reply = createOrder(order).reply().getValue();

        assertThat(reply).isEqualTo(order);
    }

    @Test
    public void getOrder() {
        Order order = new Order("1", Map.of("milk", 1), OrderState.CREATED);

        createOrder(order);

        assertThat(getOrder(order.id).reply().getValue()).isEqualTo(order);
    }

    @Test
    public void dontGetMissingOrder() {
        assertThat(getOrder("no existing id").reply().isError()).isTrue();
    }


    private EventSourcedBehaviorTestKit.CommandResultWithReply<Command, Events.Event, State, StatusReply<Order>> createOrder(Order order) {
        return eventSourcedTestKit.runCommand(replyTo -> new Create(order, replyTo));
    }

    private EventSourcedBehaviorTestKit.CommandResultWithReply<Command, Events.Event, State, StatusReply<Order>> getOrder(String orderId) {
        return eventSourcedTestKit.runCommand(replyTo -> new Get(orderId, replyTo));
    }
}