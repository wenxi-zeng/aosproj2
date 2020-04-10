package commands;

import clock.AckVector;
import clock.LogicClock;
import commonmodels.PhysicalNode;
import commonmodels.transport.Request;
import commonmodels.transport.Response;
import drivers.FileServer;
import managers.FileManager;
import ring.LookupTable;
import util.Config;
import util.FileHelper;
import util.SimpleLog;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Semaphore;

public enum CommonCommand implements Command {

    APPEND {
        @Override
        public Response execute(Request request) {
            SimpleLog.v(Config.getInstance().getId() + " receives: \"" + request.getAttachment() + "\" "  + "for file #" + request.getHeader() + " at time: " + request.getTimestamp());

            List<PhysicalNode> replicas = LookupTable.getInstance().lookup(request.getHeader());
            String inactiveReplicas = LookupTable.getInstance().lookupInactive(request.getHeader());
            if (replicas.size() == 1)
                return new Response(request)
                        .withStatus(Response.STATUS_FAILED)
                        .withMessage("append failed because only 1 replica is available. failed replicas: " + inactiveReplicas)
                        .withTimestamp(LogicClock.getInstance().getClock());

            LogicClock.getInstance().increment();
            Request mutexRequest = new Request().withType(CommonCommand.REQUEST.name())
                    .withTimestamp(LogicClock.getInstance().getClock());
            FileServer.getInstance().asyncBroadcast(mutexRequest, replicas);

            Request localRecord = (Request) request.clone();
            localRecord.withSender(Config.getInstance().getAddress())
                    .withTimestamp(LogicClock.getInstance().getClock());
            FileManager.getInstance().serve(localRecord);
            try {
                localRecord.setProcessed(new Semaphore(0));
                localRecord.getProcessed().acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Request appendRequest = (Request) request.clone();
            appendRequest.withType(CommonCommand.APPEND_ONLY.name())
                    .withTimestamp(LogicClock.getInstance().getClock());
            FileServer.getInstance().broadcast(appendRequest, replicas);

            LogicClock.getInstance().increment();
            Request releaseRequest = (Request) request.clone();
            releaseRequest.withType(CommonCommand.RELEASE.name())
                    .withTimestamp(LogicClock.getInstance().getClock());
            FileServer.getInstance().broadcast(releaseRequest, replicas);

            return new Response(request)
                    .withMessage(replicas.size() > 2 ? "successful append" : "successful append to 2 replica. replica " + inactiveReplicas + " is not available")
                    .withTimestamp(LogicClock.getInstance().getClock());
        }
    },

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
            request.withType(CommonCommand.ACK.name())
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
    },

    READ {
        @Override
        public Response execute(Request request) {
            String content;
            try {
                content = FileHelper.read(Config.getInstance().getId(), request.getHeader());
            } catch (IOException e) {
                content = "Read error. File may not exist. " + e.getMessage();
            }

            return new Response(request)
                    .withMessage(content)
                    .withTimestamp(LogicClock.getInstance().getClock());
        }
    }

}
