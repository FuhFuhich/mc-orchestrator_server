package com.example.mine_com_server.model;

public final class AuditActions {

    private AuditActions() {
    }

    public static final String START_SERVER   = "START_SERVER";
    public static final String STOP_SERVER    = "STOP_SERVER";
    public static final String RESTART_SERVER = "RESTART_SERVER";

    public static final String CREATE_SERVER              = "CREATE_SERVER";
    public static final String UPDATE_SERVER              = "UPDATE_SERVER";
    public static final String DELETE_SERVER              = "DELETE_SERVER";
    public static final String DELETE_SERVER_DEVICE_ONLY  = "DELETE_SERVER_DEVICE_ONLY";

    public static final String DEPLOY_SERVER   = "DEPLOY_SERVER";
    public static final String REDEPLOY_SERVER = "REDEPLOY_SERVER";
    public static final String UNDEPLOY_SERVER = "UNDEPLOY_SERVER";
    public static final String DEPLOY_DOCKER   = "DEPLOY_DOCKER";

    public static final String CREATE_BACKUP  = "CREATE_BACKUP";
    public static final String RESTORE_BACKUP = "RESTORE_BACKUP";
    public static final String DELETE_BACKUP  = "DELETE_BACKUP";

    public static final String ADD_MEMBER    = "ADD_MEMBER";
    public static final String REMOVE_MEMBER = "REMOVE_MEMBER";

    public static final String SEND_COMMAND  = "SEND_COMMAND";
    public static final String RCON_COMMAND  = "RCON_COMMAND";

    public static final String CREATE_NODE = "CREATE_NODE";
    public static final String DELETE_NODE = "DELETE_NODE";
    public static final String UPDATE_NODE = "UPDATE_NODE";

    public static final String FILE_READ   = "FILE_READ";
    public static final String FILE_WRITE  = "FILE_WRITE";
    public static final String FILE_DELETE = "FILE_DELETE";
}
