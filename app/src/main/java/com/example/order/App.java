package com.example.order;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;
import com.example.order.domain.OrderActor;
import com.example.order.domain.FulfillmentActor;
import com.example.order.persistence.PersistenceActor;
import lombok.extern.slf4j.Slf4j;
import com.example.order.http.OrderRoutes;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletionStage;

@Slf4j
public class App {
    static void startHttpServer(Route route, ActorSystem<?> system) {
        CompletionStage<ServerBinding> futureBinding =
                Http.get(system).newServerAt("localhost", 8080).bind(route);

        futureBinding.whenComplete((binding, exception) -> {
            if (binding != null) {
                InetSocketAddress address = binding.localAddress();
                system.log().info("Server online at http://{}:{}/",
                        address.getHostString(),
                        address.getPort());
            } else {
                system.log().error("Failed to bind HTTP endpoint, terminating system", exception);
                system.terminate();
            }
        });
    }

    public static void main(String[] args) {
        Behavior<NotUsed> rootBehavior = Behaviors.setup(context -> {
            ActorRef<PersistenceActor.PersistenceCommand> orderPersistenceActor =
                    context.spawn(PersistenceActor.create(), "PersistenceActor");

            ActorRef<FulfillmentActor.Command> fulfillmentActor =
                    context.spawn(FulfillmentActor.create(), "FulfillmentActor");

            ActorRef<OrderActor.Command> orderActor =
                    context.spawn(OrderActor.create(orderPersistenceActor, fulfillmentActor), "OrderActor");

            OrderRoutes orderRoutes = new OrderRoutes(context.getSystem(), orderPersistenceActor, orderActor);
            startHttpServer(orderRoutes.userRoutes(), context.getSystem());

            return Behaviors.empty();
        });

        ActorSystem.create(rootBehavior, "OrderSystem");
    }
}