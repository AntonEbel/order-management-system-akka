package com.example.order.http;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import akka.pattern.StatusReply;
import com.example.order.Config;
import com.example.order.domain.OrderActor;
import com.example.order.persistence.InvalidStateChangeException;
import com.example.order.persistence.Order;
import com.example.order.persistence.OrderNotFoundException;
import com.example.order.persistence.PersistenceActor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static akka.http.javadsl.server.Directives.*;
import static com.example.order.Config.TIMEOUT;
import static com.example.order.persistence.FulfillmentResult.NO_RESULT;
import static com.example.order.persistence.OrderState.CREATED;

@Slf4j
public class OrderRoutes {

    private final Scheduler scheduler;
    private final ActorRef<PersistenceActor.PersistenceCommand> orderPersistenceActor;
    private final ActorRef<OrderActor.Command> orderActor;

    public OrderRoutes(ActorSystem<?> system,
                       ActorRef<PersistenceActor.PersistenceCommand> orderPersistenceActor,
                       ActorRef<OrderActor.Command> orderActor) {
        scheduler = system.scheduler();
        this.orderPersistenceActor = orderPersistenceActor;
        this.orderActor = orderActor;
    }


    public Route userRoutes() {
        return pathPrefix("orders", () ->
                concat(
                        pathEnd(() ->
                                concat(
                                        postOrder()
                                )
                        ),
                        path(PathMatchers.segment(), (String orderId) ->
                                concat(
                                        getOrder(orderId),
                                        patchOrder(orderId)
                                )
                        )

                )
        );
    }

    private Route postOrder() {
        return post(() -> entity(
                Jackson.unmarshaller(OrderPostDto.class),
                order -> onSuccess(askForOrderCreation(order), onOrderCreationAsked())
                )
        );
    }

    private Function<StatusReply<Order>, Route> onOrderCreationAsked() {
        return reply -> {
            log.info("Create result with order: {}", reply.getValue());
            return complete(StatusCodes.CREATED, reply.getValue(), Jackson.marshaller());
        };
    }

    private CompletionStage<StatusReply<Order>> askForOrderCreation(OrderPostDto order) {
        return AskPattern.ask(orderPersistenceActor, replyTo -> new PersistenceActor.Create(
                new Order(UUID.randomUUID().toString(), order.getItems(), CREATED, NO_RESULT), replyTo), TIMEOUT, scheduler);
    }

    private Route getOrder(String orderId) {
        return get(() -> onSuccess(askForOrder(orderId), onOrderAsked()));
    }

    private Function<StatusReply<Order>, Route> onOrderAsked() {
        return reply -> {
            if (reply.isError()) {
                return complete(StatusCodes.NOT_FOUND, reply.getError().getMessage(), Jackson.marshaller());
            }
            return complete(StatusCodes.OK, reply.getValue(), Jackson.marshaller());
        };
    }

    private CompletionStage<StatusReply<Order>> askForOrder(String orderId) {
        return AskPattern.ask(orderPersistenceActor, replyTo -> new PersistenceActor.Get(orderId, replyTo), TIMEOUT, scheduler);
    }

    private Route patchOrder(String orderId) {
        return patch(() ->
                entity(
                        Jackson.unmarshaller(OrderPatchDto.class),
                        order -> onSuccess(askForChangeState(orderId, order), onChangeStateAsked())));
    }

    private CompletionStage<StatusReply<Order>> askForChangeState(String orderId, OrderPatchDto order) {
        return AskPattern.ask(orderActor, replyTo -> new OrderActor.ChangeState(orderId, order.state, replyTo), TIMEOUT, scheduler);
    }

    private Function<StatusReply<Order>, Route> onChangeStateAsked() {
        return reply -> {
            if (reply.isError()) {
                if(reply.getError() instanceof OrderNotFoundException){
                    return complete(StatusCodes.NOT_FOUND, reply.getError().getMessage(), Jackson.marshaller());
                }else if(reply.getError() instanceof InvalidStateChangeException){
                    return complete(StatusCodes.BAD_REQUEST, reply.getError().getMessage(), Jackson.marshaller());
                }else{
                    return complete(StatusCodes.INTERNAL_SERVER_ERROR, reply.getError().getMessage(), Jackson.marshaller());
                }
            }
            return complete(StatusCodes.OK, reply.getValue(), Jackson.marshaller());
        };
    }

}
