package dev.abstratium.core.filter;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.hibernate.exception.ConstraintViolationException;

import java.sql.SQLException;

/**
 * Test-only JAX-RS resource that throws Hibernate ConstraintViolationException
 * with different underlying SQL errors. Used by ConstraintViolationExceptionMapperTest
 * to verify exception mapping without depending on demo entities or database tables.
 */
@Path("/api/test/constraint")
public class ConstraintViolationTestResource {

    @GET
    @Path("/mysql-duplicate")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerMySqlDuplicate() {
        SQLException sqlException = new SQLException(
            "Duplicate entry 'temp' for key 'T_test.UQ_test_name'", "23000", 1062);
        throw new ConstraintViolationException(
            "could not execute statement", sqlException, "UQ_test_name");
    }

    @GET
    @Path("/h2-unique")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerH2Unique() {
        SQLException sqlException = new SQLException(
            "Unique index or primary key violation: \"UQ_TEST_NAME ON T_TEST(NAME)\"", "23505", 23505);
        throw new ConstraintViolationException(
            "could not execute statement", sqlException, "UQ_test_name");
    }

    @GET
    @Path("/fk-violation")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerFkViolation() {
        SQLException sqlException = new SQLException(
            "Cannot add or update a child row: a foreign key constraint fails", "23000", 1452);
        throw new ConstraintViolationException(
            "could not execute statement", sqlException, "FK_some_fk");
    }

    @GET
    @Path("/uq-name-only")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerUqNameOnly() {
        throw new ConstraintViolationException(
            "could not execute statement",
            new SQLException("constraint violation", "23000", 1),
            "UQ_test_name");
    }

    @GET
    @Path("/null-message")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerNullMessage() {
        SQLException sqlException = new SQLException(
            "Duplicate entry 'x' for key 'T_test.UQ_test_name'", "23000", 1062);
        throw new ConstraintViolationException(null, sqlException, "UQ_test_name");
    }

    @GET
    @Path("/null-cause")
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerNullCause() {
        throw new ConstraintViolationException(null, null, "FK_some_fk");
    }
}
