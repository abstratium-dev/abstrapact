package dev.abstratium.demo;

/**
 * Represents the standard roles for the service.
 */
public interface Roles {
    /** The oauth client_id for the service */
    String CLIENT_ID = "abstratium-abstrapact";

    /** The user is simply that. Used to ensure that they can only call some APIs if they are also signed in. */
    String USER = CLIENT_ID + "_user";
}
