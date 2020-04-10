package drivers;

import clock.LogicClock;
import commands.CommonCommand;
import commonmodels.PhysicalNode;
import commonmodels.transport.InvalidRequestException;
import commonmodels.transport.Request;
import commonmodels.transport.Response;
import ring.LookupTable;
import ring.Terminal;
import socket.SocketClient;
import util.Config;
import util.MathX;
import util.SimpleLog;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Semaphore;

public class FileClient {

    private SocketClient socketClient;

    private Semaphore semaphore;

    private SocketClient.ServerCallBack callBack = new SocketClient.ServerCallBack() {
        @Override
        public void onResponse(Request request, Response response) {
            LogicClock.getInstance().increment(response.getTimestamp());
            SimpleLog.v(request.getSenderId() + " receives a successful ack from " + request.getReceiverId());
            semaphore.release();
        }

        @Override
        public void onFailure(Request request, String error) {
            SimpleLog.v(request.getSenderId() + " receives failure from " + request.getReceiverId() + ", error message: " + error);
            semaphore.release();
        }
    };

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println ("Usage: FileClient <-a | -t>");
            return;
        }

        FileClient client = new FileClient();

        if (args.length > 1)
            Config.getInstance().setId(args[1]);
        int daemonPort = Config.PORT;
        if (args.length > 2)
        {
            try
            {
                daemonPort = Integer.parseInt(args[2]);
            }
            catch (NumberFormatException e)
            {
                System.err.println ("Invalid server port: " + e);
                return;
            }
            if (daemonPort <= 0 || daemonPort > 65535)
            {
                System.err.println ("Invalid server port");
                return;
            }
        }

        String address = getAddress();
        Config.with(address, daemonPort);
        SimpleLog.with(address, daemonPort);

        if (args[0].equals("-a"))
            client.start();
        else
            client.launchTerminal();
    }

    public FileClient() {
        socketClient = SocketClient.getInstance();
        semaphore = new Semaphore(0);
    }

    private String choseFile() {
        List<String> files = Config.getInstance().getFiles();
        return files.get(MathX.nextInt(files.size()));
    }

    private void generateRequest() {
        int remainingActions = 100;

        while (remainingActions > 0) {
            try {
                Thread.sleep(MathX.nextInt(0, 1000));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            String file = choseFile();
            PhysicalNode node = LookupTable.getInstance().chooseServer(file);
            long timestamp = LogicClock.getInstance().getClock();

            Request request = new Request()
                    .withAttachment(Config.getInstance().getId() + " message #" + (101 - remainingActions) + " -- " + node.getId())
                    .withHeader(file)
                    .withReceiver(node.getAddress())
                    .withSender(Config.getInstance().getAddress())
                    .withReceiverId(node.getId())
                    .withSenderId(Config.getInstance().getId())
                    .withTimestamp(timestamp)
                    .withType(CommonCommand.APPEND.name());

            SimpleLog.v(Config.getInstance().getId() + " requests: \"" + request.getAttachment() + "\" "  + "for file #" + file + " at time: " + timestamp);
            socketClient.send(node.getAddress(), node.getPort(), request, callBack);
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            remainingActions--;
        }
    }

    public void start() {
        SimpleLog.v(Config.getInstance().getId() + " " + "starts at time: " + LogicClock.getInstance().getClock());
        generateRequest();
        onFinished();
        SimpleLog.v(Config.getInstance().getId() + "  gracefully shutdown.");
        System.exit(0);
    }

    private static String getAddress() {
        try {
            InetAddress id = InetAddress.getLocalHost();
            return id.getHostName();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void onFinished(){
        socketClient.stop();
    }

    private void launchTerminal() {
        Scanner in = new Scanner(System.in);
        String command = in.nextLine();
        Terminal terminal = new Terminal();
        terminal.printInfo();

        while (!command.equalsIgnoreCase("exit")){
            try {
                Request request = terminal.translate(command);
                if (request.getType().equals(CommonCommand.DISRUPT_LOCAL.name()) || request.getType().equals(CommonCommand.RESUME_LOCAL.name())) {
                    CommonCommand cmd = CommonCommand.valueOf(request.getType());
                    Response response = cmd.execute(request);
                    SimpleLog.v(response.getMessage());
                }
                else {
                    if (LookupTable.getInstance().getNode(request.getReceiverId()).isActive()) {
                        SimpleLog.v(Config.getInstance().getId() + " requests: \"" + request.getAttachment() + "\" " + "for file #" + request.getHeader() + " at time: " + request.getTimestamp());
                        socketClient.send(request.getReceiver(), request, callBack);
                        try {
                            semaphore.acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    else {
                        SimpleLog.v("Selected server " + request.getReceiverId() + " is unreachable at time: " + request.getTimestamp());
                    }
                }

                command = in.nextLine();
            } catch (InvalidRequestException e) {
                e.printStackTrace();
            }
        }
    }
}
