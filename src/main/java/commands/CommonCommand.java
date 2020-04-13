package commands;

import clock.LogicClock;
import commonmodels.PhysicalNode;
import commonmodels.transport.InvalidRequestException;
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

public enum CommonCommand implements Command, ConvertableCommand{

    APPEND {
        @Override
        public Request convertToRequest(String[] args) throws InvalidRequestException {
            if (args.length != 3 && args.length != 4) {
                throw new InvalidRequestException("Wrong arguments. Try: " + getHelpString());
            }

            String file = args[1];
            int r = -1;
            try {
                r = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {}

            PhysicalNode node = LookupTable.getInstance().chooseServer(file, r);
            if (node == null) {
                throw new InvalidRequestException("No replica available for " + file);
            }

            String attachment = "";
            if (args.length == 4)
                attachment = args[3];
            else
                attachment = Config.getInstance().getId() + " randomly generated message  at " + LogicClock.getInstance().getClock() + " -- " + node.getId();

            long timestamp = LogicClock.getInstance().getClock();

            return new Request()
                    .withAttachment(attachment)
                    .withHeader(file)
                    .withReceiver(node.getAddress())
                    .withSender(Config.getInstance().getAddress())
                    .withReceiverId(node.getId())
                    .withSenderId(Config.getInstance().getId())
                    .withTimestamp(timestamp)
                    .withType(CommonCommand.APPEND.name());
        }

        @Override
        public String getParameterizedString() {
            return CommonCommand.APPEND.name() + " %s %s %s";
        }

        @Override
        public String getHelpString() {
            return String.format(getParameterizedString(), "<filename>", "<replica>", "[content]");
        }

        @Override
        public Response execute(Request request) {
            SimpleLog.v(Config.getInstance().getId() + " receives: \"" + request.getAttachment() + "\" "  + "for file #" + request.getHeader() + " at time: " + request.getTimestamp());

            List<PhysicalNode> replicas = LookupTable.getInstance().lookup(request.getHeader());
            String inactiveReplicas = LookupTable.getInstance().lookupInactive(request.getHeader());
            if (replicas.size() == 0)
                return new Response(request)
                        .withStatus(Response.STATUS_FAILED)
                        .withMessage("append failed because only 1 replica is available. failed replicas: " + inactiveReplicas)
                        .withTimestamp(LogicClock.getInstance().getClock());

            FileServer.Broadcaster broadcaster = new FileServer.Broadcaster();
            long clock = LogicClock.getInstance().increment();
            Request mutexRequest = new Request().withType(ServerCommand.REQUEST.name())
                    .withHeader(request.getHeader())
                    .withTimestamp(clock);
            broadcaster.asyncBroadcast(mutexRequest, replicas);

            Request localRecord = (Request) request.clone();
            localRecord.withSender(Config.getInstance().getAddress())
                    .withTimestamp(mutexRequest.getTimestamp());
            FileManager.getInstance().serve(localRecord);
            try {
                localRecord.setProcessed(new Semaphore(0));
                localRecord.getProcessed().acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Request appendRequest = (Request) request.clone();
            appendRequest.withType(ServerCommand.APPEND_ONLY.name())
                    .withTimestamp(LogicClock.getInstance().getClock());
            broadcaster.broadcast(appendRequest, replicas);

            clock = LogicClock.getInstance().increment();
            Request releaseRequest = (Request) request.clone();
            releaseRequest.withType(ServerCommand.RELEASE.name())
                    .withTimestamp(clock);
            broadcaster.asyncBroadcast(releaseRequest, replicas);

            return new Response(request)
                    .withMessage(replicas.size() > 1 ? "successful append" : "successful append to 2 replica. replica " + inactiveReplicas + " is not available")
                    .withTimestamp(LogicClock.getInstance().getClock());
        }
    },


    READ {
        @Override
        public Request convertToRequest(String[] args) throws InvalidRequestException {
            if (args.length != 2 && args.length != 3) {
                throw new InvalidRequestException("Wrong arguments. Try: " + getHelpString());
            }

            int r = -1;
            if (args.length == 3) {
                try {
                    r = Integer.parseInt(args[2]);
                } catch (NumberFormatException ignored) {
                }
            }

            PhysicalNode node = LookupTable.getInstance().chooseServer(args[1], r);
            if (node == null) {
                throw new InvalidRequestException("No replica available for " + args[1]);
            }
            long timestamp = LogicClock.getInstance().getClock();

            return new Request().withType(CommonCommand.READ.name())
                    .withHeader(args[1])
                    .withReceiver(node.getAddress())
                    .withSender(Config.getInstance().getAddress())
                    .withReceiverId(node.getId())
                    .withSenderId(Config.getInstance().getId())
                    .withTimestamp(timestamp);
        }

        @Override
        public String getParameterizedString() {
            return CommonCommand.READ.name() + " %s %s";
        }

        @Override
        public String getHelpString() {
            return String.format(getParameterizedString(), "<filename>", "[replica]");
        }

        @Override
        public Response execute(Request request) {
            String content;
            try {
                content = FileHelper.read(Config.getInstance().getId(), request.getHeader());
            } catch (IOException e) {
                content = "Read error. " + e.getMessage();
            }

            return new Response(request)
                    .withMessage(content)
                    .withTimestamp(LogicClock.getInstance().getClock());
        }
    },

    DISRUPT {
        @Override
        public Request convertToRequest(String[] args) throws InvalidRequestException {
            if (args.length != 3) {
                throw new InvalidRequestException("Wrong arguments. Try: " + getHelpString());
            }

            PhysicalNode node1 = LookupTable.getInstance().getNode(args[1]);
            PhysicalNode node2 = LookupTable.getInstance().getNode(args[2]);
            if (node1 == null || node2 == null) {
                throw new InvalidRequestException("Invalid node id");
            }

            return new Request().withType(CommonCommand.DISRUPT.name())
                    .withReceiverId(node1.getId())
                    .withReceiver(node1.getAddress())
                    .withSender(Config.getInstance().getAddress())
                    .withSenderId(Config.getInstance().getId())
                    .withAttachment(node2.getId())
                    .withTimestamp(LogicClock.getInstance().getClock());
        }

        @Override
        public String getParameterizedString() {
            return CommonCommand.DISRUPT.name() + " %s %s";
        }

        @Override
        public String getHelpString() {
            return String.format(getParameterizedString(), "<sender>", "<receiver>");
        }

        @Override
        public Response execute(Request request) {
            String id = request.getAttachment();
            LookupTable.getInstance().disrupt(id);
            return new Response(request)
                    .withMessage("Channel " + Config.getInstance().getId() + " -> " + id + " has been disrupted")
                    .withTimestamp(LogicClock.getInstance().getClock());
        }
    },

    RESUME {
        @Override
        public Request convertToRequest(String[] args) throws InvalidRequestException {
            if (args.length != 3) {
                throw new InvalidRequestException("Wrong arguments. Try: " + getHelpString());
            }

            PhysicalNode node1 = LookupTable.getInstance().getNode(args[1]);
            PhysicalNode node2 = LookupTable.getInstance().getNode(args[2]);
            if (node1 == null || node2 == null) {
                throw new InvalidRequestException("Invalid node id");
            }

            return new Request().withType(CommonCommand.RESUME.name())
                    .withReceiverId(node1.getId())
                    .withReceiver(node1.getAddress())
                    .withSender(Config.getInstance().getAddress())
                    .withSenderId(Config.getInstance().getId())
                    .withAttachment(node2.getId())
                    .withTimestamp(LogicClock.getInstance().getClock());
        }

        @Override
        public String getParameterizedString() {
            return CommonCommand.RESUME.name() + " %s %s";
        }

        @Override
        public String getHelpString() {
            return String.format(getParameterizedString(), "<sender>", "<receiver>");
        }

        @Override
        public Response execute(Request request) {
            String id = request.getAttachment();
            LookupTable.getInstance().resume(id);
            return new Response(request)
                    .withMessage("Channel " + Config.getInstance().getId() + " -> " + id + " has been resumed")
                    .withTimestamp(LogicClock.getInstance().getClock());
        }
    },

    DISRUPT_LOCAL {
        @Override
        public Request convertToRequest(String[] args) throws InvalidRequestException {
            if (args.length != 2) {
                throw new InvalidRequestException("Wrong arguments. Try: " + getHelpString());
            }

            PhysicalNode node1 = LookupTable.getInstance().getNode(args[1]);
            if (node1 == null) {
                throw new InvalidRequestException("Invalid node id");
            }

            return new Request().withType(CommonCommand.DISRUPT_LOCAL.name())
                    .withAttachment(node1.getId());
        }

        @Override
        public String getParameterizedString() {
            return CommonCommand.DISRUPT_LOCAL.name() + " %s";
        }

        @Override
        public String getHelpString() {
            return String.format(getParameterizedString(), "<remote>");
        }

        @Override
        public Response execute(Request request) {
            String id = request.getAttachment();
            LookupTable.getInstance().disrupt(id);
            return new Response(request)
                    .withMessage("Local channel to remote " + id + " has been disrupted")
                    .withTimestamp(LogicClock.getInstance().getClock());
        }
    },

    RESUME_LOCAL {
        @Override
        public Request convertToRequest(String[] args) throws InvalidRequestException {
            if (args.length != 2) {
                throw new InvalidRequestException("Wrong arguments. Try: " + getHelpString());
            }

            PhysicalNode node1 = LookupTable.getInstance().getNode(args[1]);
            if (node1 == null) {
                throw new InvalidRequestException("Invalid node id");
            }

            return new Request().withType(CommonCommand.RESUME_LOCAL.name())
                    .withAttachment(node1.getId());
        }

        @Override
        public String getParameterizedString() {
            return CommonCommand.RESUME_LOCAL.name() + " %s";
        }

        @Override
        public String getHelpString() {
            return String.format(getParameterizedString(), "<remote>");
        }

        @Override
        public Response execute(Request request) {
            String id = request.getAttachment();
            LookupTable.getInstance().resume(id);
            return new Response(request)
                    .withMessage("Local channel to remote " + id + " has been resumed")
                    .withTimestamp(LogicClock.getInstance().getClock());
        }
    },

    LIST {
        @Override
        public Request convertToRequest(String[] args) throws InvalidRequestException {
            if (args.length != 2) {
                throw new InvalidRequestException("Wrong arguments. Try: " + getHelpString());
            }

            return new Request().withType(CommonCommand.LIST.name())
                    .withAttachment(args[1]);
        }

        @Override
        public String getParameterizedString() {
            return CommonCommand.LIST.name() + " %s";
        }

        @Override
        public String getHelpString() {
            return String.format(getParameterizedString(), "<file name>");
        }

        @Override
        public Response execute(Request request) {
            String filename = request.getAttachment();
            List<PhysicalNode> replicas = LookupTable.getInstance().lookup(filename);
            StringBuilder result = new StringBuilder();
            for (PhysicalNode p : replicas)
                result.append(p.getId()).append(" ");
            return new Response(request)
                    .withMessage(result.toString())
                    .withTimestamp(LogicClock.getInstance().getClock());
        }
    }

}
