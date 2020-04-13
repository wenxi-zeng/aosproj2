package commands;

import clock.AckVector;
import clock.LogicClock;
import commonmodels.transport.Request;
import commonmodels.transport.Response;
import drivers.FileServer;
import managers.FileManager;
import util.Config;
import util.FileHelper;
import util.SimpleLog;

import java.io.IOException;

public enum ServerCommand implements Command {
    APPEND_ONLY {
        @Override
        public Response execute(Request request) {
            try {
                FileHelper.append(Config.getInstance().getId(), request.getHeader(), request.getAttachment());
            } catch (IOException e) {
                e.printStackTrace();
            }

            return new Response(request)
                    .withMessage("successful append")
                    .withTimestamp(LogicClock.getInstance().getClock());
        }
    },

    RELEASE {
        @Override
        public Response execute(Request request) {
            long clock = LogicClock.getInstance().increment(request.getTimestamp());
            FileManager.getInstance().release(request);
            AckVector.getInstance().updateClock(request.getSender(), request.getTimestamp());
            return new Response(request)
                    .withMessage("successful release")
                    .withTimestamp(clock);
        }
    },

    REQUEST {
        @Override
        public Response execute(Request request) {
            LogicClock.getInstance().increment(request.getTimestamp());
            FileManager.getInstance().serve(request);
            AckVector.getInstance().updateClock(request.getSender(), request.getTimestamp());

            long clock = LogicClock.getInstance().increment();
            Request ack = (Request) request.clone();
            ack.withType(ServerCommand.ACK.name())
                    .withTimestamp(clock)
                    .withReceiver(request.getSender())
                    .withReceiverId(request.getSenderId());
            FileServer.getInstance().send(ack);

            return new Response(request)
                    .withMessage("successful request")
                    .withTimestamp(clock);
        }
    },

    ACK {
        @Override
        public Response execute(Request request) {
            long clock = LogicClock.getInstance().increment(request.getTimestamp());
            SimpleLog.v("ACK receive,  sender" + request.getSender() + ", clock: " + request.getTimestamp());
            AckVector.getInstance().updateClock(request.getSender(), request.getTimestamp());
            return new Response(request)
                    .withMessage("successful ack")
                    .withTimestamp(clock);
        }
    }
}
