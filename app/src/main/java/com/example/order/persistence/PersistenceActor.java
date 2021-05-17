package com.example.order.persistence;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.pattern.StatusReply;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.ReplyEffect;
import com.example.order.serialization.JsonSerializable;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;
import java.util.function.Supplier;

import static com.example.order.persistence.Events.*;

public class PersistenceActor extends EventSourcedBehaviorWithEnforcedReplies<PersistenceActor.PersistenceCommand, Event, State> {

    private static final List<StateTransfer> VALID_STATE_TRANSFERS = List.of(
            new StateTransfer(OrderState.CREATED, OrderState.PAID),
            new StateTransfer(OrderState.PAID, OrderState.IN_FULFILLMENT)
    );

    public PersistenceActor() {
        super(PersistenceId.ofUniqueId("Orders"));
    }

    @Override
    public State emptyState() {
        return new State();
    }

    @Override
    public EventHandler<State, Event> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(OrderCreated.class, (state, evt) -> state.addOrder(evt.order))
                .onEvent(OrderStateChanged.class, (state, evt) -> state.changeState(evt.orderId, evt.state))
                .onEvent(OrderClosed.class, (state, evt) -> state.closeOrder(evt.orderId, evt.fulfillmentResult))
                .build();
    }

    @Override
    public CommandHandlerWithReply<PersistenceCommand, Event, State> commandHandler() {
        return newCommandHandlerWithReplyBuilder()
                .forAnyState()
                .onCommand(Create.class, this::onCreate)
                .onCommand(Get.class, this::onGet)
                .onCommand(ChangeState.class, this::onChangeState)
                .onCommand(Close.class, this::onClose)
                .build();
    }

    private ReplyEffect<Event, State> onCreate(State state, Create cmd) {
        return Effect()
                .persist(new OrderCreated(cmd.order))
                .thenReply(cmd.replyTo, orders -> StatusReply.success(orders.getOrder(cmd.order.id)));
    }

    private ReplyEffect<Event, State> onGet(State state, Get cmd) {
        return state.findOrder(cmd.orderId)
                .map(order -> Effect().reply(cmd.replyTo, StatusReply.success(order)))
                .orElseGet(orderNotFoundReply(cmd.orderId, cmd.replyTo));
    }

    private ReplyEffect<Event, State> onChangeState(State state, ChangeState cmd) {
        return state.findOrder(cmd.orderId)
                .map(order -> changeState(cmd, order))
                .orElseGet(orderNotFoundReply(cmd.orderId, cmd.replyTo));
    }

    private ReplyEffect<Event, State> changeState(ChangeState cmd, Order order) {
        if(isInvalidStateChange(order.state, cmd.state)){
            return invalidStateChangeReply(order.id, cmd.replyTo);
        }
        return Effect()
                .persist(new OrderStateChanged(order.id, cmd.state))
                .thenReply(cmd.replyTo, updatedOrders -> StatusReply.success(updatedOrders.getOrder(cmd.orderId)));
    }

    private ReplyEffect<Event, State> onClose(State state, Close cmd) {
        return state.findOrder(cmd.orderId)
                .map(order -> close(cmd, order))
                .orElseGet(orderNotFoundReply(cmd.orderId, cmd.replyTo));
    }

    private ReplyEffect<Event, State> close(Close cmd, Order order) {
        if (order.state != OrderState.IN_FULFILLMENT) return invalidStateChangeReply(cmd.orderId, cmd.replyTo);
        return Effect()
                .persist(new OrderClosed(order.id, cmd.fulfillmentResult))
                .thenReply(cmd.replyTo, updatedOrders -> StatusReply.success(updatedOrders.getOrder(cmd.orderId)));
    }

    private Supplier<ReplyEffect<Event, State>> orderNotFoundReply(String orderId, ActorRef<StatusReply<Order>> replyTo) {
        return () -> Effect().reply(replyTo, StatusReply.error(
                new OrderNotFoundException(String.format("Order with ID %s not found!", orderId))));
    }

    private boolean isInvalidStateChange(OrderState from, OrderState to) {
        return !VALID_STATE_TRANSFERS.contains(new StateTransfer(from, to));
    }

    private ReplyEffect<Event, State> invalidStateChangeReply(String orderId, ActorRef<StatusReply<Order>> replyTo) {
        return Effect().reply(replyTo, StatusReply.error(
                new InvalidStateChangeException(String.format("Invalid state change for Order with ID %s!", orderId))));
    }

    public static Behavior<PersistenceCommand> create() {
        return new PersistenceActor();
    }

    public interface PersistenceCommand extends JsonSerializable {
    }

    @AllArgsConstructor
    public static final class Create implements PersistenceCommand {
        public final Order order;
        public final ActorRef<StatusReply<Order>> replyTo;
    }

    @AllArgsConstructor
    public static final class Get implements PersistenceCommand {
        public final String orderId;
        public final ActorRef<StatusReply<Order>> replyTo;
    }

    @AllArgsConstructor
    public static final class ChangeState implements PersistenceCommand {
        public final String orderId;
        public final OrderState state;
        public final ActorRef<StatusReply<Order>> replyTo;
    }

    @AllArgsConstructor
    public static final class Close implements PersistenceCommand {
        public final String orderId;
        public final FulfillmentResult fulfillmentResult;
        public final ActorRef<StatusReply<Order>> replyTo;
    }

    @Value
    private static class StateTransfer {
        OrderState from;
        OrderState to;
    }
}
