package com.pluginbans.velocity;

public record VelocityConfig(
        VelocityDatabase database,
        VelocityMessages messages,
        String nnrHiddenReason
) {
    public record VelocityDatabase(
            String type,
            String host,
            int port,
            String database,
            String user,
            String password,
            int poolSize
    ) {
    }
}
