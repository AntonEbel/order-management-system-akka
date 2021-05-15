package com.example.order.persistence;

import akka.actor.typed.ActorRef;
import akka.pattern.StatusReply;
import com.example.order.serialization.JsonSerializable;
import lombok.AllArgsConstructor;

public class Commands {

    public interface Command extends JsonSerializable {
    }

    @AllArgsConstructor
    public static final class Create implements Command {
        public final Order order;
        public final ActorRef<StatusReply<Order>> replyTo;
    }

    @AllArgsConstructor
    public static final class Get implements Command {
        public final String orderId;
        public final ActorRef<StatusReply<Order>> replyTo;
    }
}
