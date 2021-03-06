package org.kie.internal.command;

import org.kie.api.command.Command;
import org.kie.api.command.KieCommands;

public interface ExtendedKieCommands extends KieCommands {

    public Command newEnableAuditLog( String directory, String filename );

    public Command newEnableAuditLog( String filename );

    public Command newClearActivationGroup(String name);

    public Command newClearAgenda();

    public Command newClearAgendaGroup(String name);

    public Command newClearRuleFlowGroup(String name);
}
