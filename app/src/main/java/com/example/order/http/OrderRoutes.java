package com.example.order.http;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import akka.pattern.StatusReply;
import com.example.order.persistence.Commands;
import com.example.order.persistence.Order;
import com.example.order.persistence.OrderState;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static akka.http.javadsl.server.Directives.*;

@Slf4j
public class OrderRoutes {

    private final Duration timeout;
    private final Scheduler scheduler;
    private ActorRef<Commands.Command> orderActor;

    public OrderRoutes(ActorContext<NotUsed> ctx, ActorRef<Commands.Command> orderActor) {
        scheduler = ctx.getSystem().scheduler();
        timeout = ctx.getSystem().settings().config().getDuration("my-app.routes.ask-timeout");
        this.orderActor = orderActor;
    }


    public Route userRoutes() {
        return pathPrefix("orders", () ->
                concat(
                        pathEnd(() ->
                                concat(
                                        post(() ->
                                                entity(
                                                        Jackson.unmarshaller(OrderDto.class),
                                                        order -> onSuccess(createOrder(order), completeCreateOrder())
                                                )
                                        )
                                )
                        ),
                        path(PathMatchers.segment(), (String orderId) ->
                                concat(
                                        get(() ->
                                                rejectEmptyResponse(() -> onSuccess(getOrder(orderId), completeGetOrder()))
                                        )
                                )
                        )

                )
        );
    }

    private CompletionStage<StatusReply<Order>> createOrder(OrderDto orderDto) {
        Order order = new Order(UUID.randomUUID().toString(), orderDto.getItems(), OrderState.CREATED);
        return AskPattern.ask(orderActor, replyTo -> new Commands.Create(order, replyTo), timeout, scheduler);
    }

    private Function<StatusReply<Order>, Route> completeCreateOrder() {
        return reply -> {
            log.info("Create result with order: {}", reply.getValue());
            return complete(StatusCodes.CREATED, reply.getValue(), Jackson.marshaller());
        };
    }

    private CompletionStage<StatusReply<Order>> getOrder(String orderId) {
        return AskPattern.ask(orderActor, replyTo -> new Commands.Get(orderId, replyTo), timeout, scheduler);
    }

    private Function<StatusReply<Order>, Route> completeGetOrder() {
        return reply -> {
            if (reply.isError()) {
                return complete(StatusCodes.NOT_FOUND, reply.getError().getMessage(), Jackson.marshaller());
            }
            return complete(StatusCodes.OK, reply.getValue(), Jackson.marshaller());
        };
    }
}
