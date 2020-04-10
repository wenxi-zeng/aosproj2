package commands;

import commonmodels.transport.InvalidRequestException;
import commonmodels.transport.Request;;

public interface ConvertableCommand {
    Request convertToRequest(String[] args) throws InvalidRequestException;
    String getParameterizedString();
    String getHelpString();
}
