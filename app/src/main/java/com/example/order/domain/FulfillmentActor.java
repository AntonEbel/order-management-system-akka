package com.example.order.domain;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import lombok.AllArgsConstructor;

import java.util.concurrent.ThreadLocalRandom;

import static com.example.order.domain.FulfillmentActor.Command;
import static com.example.order.persistence.FulfillmentResult.FAILURE;
import static com.example.order.persistence.FulfillmentResult.SUCCESS;

public class FulfillmentActor extends AbstractBehavior<Command> {

    public FulfillmentActor(ActorContext<Command> ctx) {
        super(ctx);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Fulfill.class, this::onFulfill)
                .build();
    }

    public Behavior<Command> onFulfill(Fulfill cmd) {
        var result = ThreadLocalRandom.current().nextInt(0, 2) == 0 ? SUCCESS : FAILURE;
        cmd.replyTo.tell(new OrderActor.Close(cmd.orderId, result));
        return this;
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(FulfillmentActor::new);
    }

    public interface Command {

    }

    @AllArgsConstructor
    public static class Fulfill implements Command {
        String orderId;
        public final ActorRef<OrderActor.Command> replyTo;
    }
}
