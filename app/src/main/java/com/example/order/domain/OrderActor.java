package com.example.order.domain;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.*;
import akka.pattern.StatusReply;
import com.example.order.fulfillment.FulfillmentActor;
import com.example.order.persistence.FulfillmentResult;
import com.example.order.persistence.Order;
import com.example.order.persistence.OrderState;
import com.example.order.persistence.PersistenceActor;
import com.example.order.persistence.PersistenceActor.PersistenceCommand;
import lombok.AllArgsConstructor;

import java.util.concurrent.CompletionStage;

import static com.example.order.Config.TIMEOUT;
import static com.example.order.persistence.OrderState.IN_FULFILLMENT;
import static com.example.order.persistence.OrderState.PAID;

public class OrderActor extends AbstractBehavior<OrderActor.Command> {

    private final ActorRef<PersistenceCommand> persistenceActor;
    private ActorRef<FulfillmentActor.Command> fulfillmentActor;
    private final Scheduler scheduler;

    public OrderActor(ActorContext<Command> ctx,
                      ActorRef<PersistenceCommand> persistenceActor,
                      ActorRef<FulfillmentActor.Command> fulfillmentActor) {
        super(ctx);
        this.persistenceActor = persistenceActor;
        this.fulfillmentActor = fulfillmentActor;
        scheduler = ctx.getSystem().scheduler();
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ChangeState.class, this::onStateChange)
                .onMessage(Close.class, this::onClose)
                .build();
    }

    private Behavior<Command> onStateChange(ChangeState cmd) {
        askPersistenceActorForStateChange(cmd.orderId, cmd.state).whenComplete((orderStatusReply, throwable) -> {
            startFulfillment(orderStatusReply);
            cmd.replyTo.tell(orderStatusReply);
        });
        return this;
    }

    private CompletionStage<StatusReply<Order>> askPersistenceActorForStateChange(String orderId, OrderState state) {
        return AskPattern.ask(persistenceActor, reply ->
                new PersistenceActor.ChangeState(orderId, state, reply), TIMEOUT, scheduler);
    }

    private void startFulfillment(StatusReply<Order> orderPaidReply) {
        if (orderPaidReply.isSuccess() && orderPaidReply.getValue().getState() == PAID) {
            askPersistenceActorForStateChange(orderPaidReply.getValue().id, IN_FULFILLMENT).whenComplete((orderFulfillmentReply, throwable) -> {
                if (orderFulfillmentReply.isSuccess()) {
                    fulfillmentActor.tell(new FulfillmentActor.Fulfill(orderPaidReply.getValue().id, getContext().getSelf()));
                }
            });
        }
    }

    private Behavior<Command> onClose(Close cmd) {
        askPersistenceActorForClosingOrder(cmd.orderId, cmd.fulfillmentResult);
        return this;
    }

    private CompletionStage<StatusReply<Order>> askPersistenceActorForClosingOrder(String orderId, FulfillmentResult result) {
        return AskPattern.ask(persistenceActor, replyTo ->
                new PersistenceActor.Close(orderId, result, replyTo), TIMEOUT, scheduler);

    }

    public static Behavior<Command> create(ActorRef<PersistenceCommand> persistenceActor,
                                           ActorRef<FulfillmentActor.Command> fulfillmentActor) {
        return Behaviors.setup(ctx -> new OrderActor(ctx, persistenceActor, fulfillmentActor));
    }

    public interface Command {

    }

    @AllArgsConstructor
    public static final class ChangeState implements Command {
        public final String orderId;
        public final OrderState state;
        public final ActorRef<StatusReply<Order>> replyTo;
    }

    @AllArgsConstructor
    public static final class Close implements Command {
        public final String orderId;
        public final FulfillmentResult fulfillmentResult;
    }
}
