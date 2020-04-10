package ring;

import commands.CommonCommand;
import commonmodels.transport.InvalidRequestException;
import commonmodels.transport.Request;
import util.URIHelper;

public class Terminal {

    public void printInfo() {
        System.out.println("\nAvailable commands:\n" +
                CommonCommand.READ.getHelpString() + "\n" +
                CommonCommand.APPEND.getHelpString() + "\n" +
                CommonCommand.DISRUPT.getHelpString() + "\n" +
                CommonCommand.RESUME.getHelpString() + "\n" +
                CommonCommand.DISRUPT_LOCAL.getHelpString() + "\n" +
                CommonCommand.RESUME_LOCAL.getHelpString() + "\n");
    }

    public Request translate(String[] args) throws InvalidRequestException {
        try {
            CommonCommand cmd = CommonCommand.valueOf(args[0].toUpperCase());
            URIHelper.verifyAddress(args);
            return cmd.convertToRequest(args);
        }
        catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Command " + args[0] + " not found");
        }
    }

    public Request translate(String command) throws InvalidRequestException {
        return translate(command.split(" "));
    }
}
