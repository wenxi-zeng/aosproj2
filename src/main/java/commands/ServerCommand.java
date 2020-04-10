package commands;

import clock.AckVector;
import clock.LogicClock;
import commonmodels.transport.Request;
import commonmodels.transport.Response;
import drivers.FileServer;
import managers.FileManager;
import util.Config;
import util.FileHelper;

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
            LogicClock.getInstance().increment(request.getTimestamp());
            FileManager.getInstance().release(request);
            AckVector.getInstance().updateClock(request.getSender(), request.getTimestamp());
            return new Response(request)
                    .withMessage("successful release")
                    .withTimestamp(LogicClock.getInstance().getClock());
        }
    },

    REQUEST {
        @Override
        public Response execute(Request request) {
            LogicClock.getInstance().increment(request.getTimestamp());
            FileManager.getInstance().serve(request);
            AckVector.getInstance().updateClock(request.getSender(), request.getTimestamp());

            LogicClock.getInstance().increment();
            request.withType(ServerCommand.ACK.name())
                    .withTimestamp(LogicClock.getInstance().getClock())
                    .withReceiver(request.getSender())
                    .withReceiverId(request.getSenderId());
            FileServer.getInstance().send(request);

            return new Response(request)
                    .withMessage("successful request")
                    .withTimestamp(LogicClock.getInstance().getClock());
        }
    },

    ACK {
        @Override
        public Response execute(Request request) {
            LogicClock.getInstance().increment(request.getTimestamp());
            AckVector.getInstance().updateClock(request.getSender(), request.getTimestamp());
            return new Response(request)
                    .withMessage("successful ack")
                    .withTimestamp(LogicClock.getInstance().getClock());
        }
    }
}
