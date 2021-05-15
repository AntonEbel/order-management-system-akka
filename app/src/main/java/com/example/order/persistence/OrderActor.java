package com.example.order.persistence;

import akka.actor.typed.Behavior;
import akka.pattern.StatusReply;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.ReplyEffect;

import static com.example.order.persistence.Commands.*;
import static com.example.order.persistence.Events.Event;
import static com.example.order.persistence.Events.OrderCreated;

public class OrderActor extends EventSourcedBehaviorWithEnforcedReplies<Command, Event, State> {

    public OrderActor() {
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
                .build();
    }

    @Override
    public CommandHandlerWithReply<Command, Event, State> commandHandler() {
        return newCommandHandlerWithReplyBuilder()
                .forAnyState()
                .onCommand(Create.class, this::onCreate)
                .onCommand(Get.class, this::onGet)
                .build();
    }

    private ReplyEffect<Event, State> onGet(State state, Get cmd) {
        return state.findOrder(cmd.orderId)
                .map(order -> Effect().reply(cmd.replyTo, StatusReply.success(order)))
                .orElseGet(() -> Effect().reply(cmd.replyTo, StatusReply.error(String.format("Order with ID %s not found!", cmd.orderId))));
    }

    private ReplyEffect<Event, State> onCreate(State state, Create cmd) {
        return Effect()
                .persist(new OrderCreated(cmd.order))
                .thenReply(cmd.replyTo, orders -> StatusReply.success(cmd.order));
    }

    public static Behavior<Command> create(){
        return new OrderActor();
    }
}
